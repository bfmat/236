import torch
from tqdm.auto import tqdm
from pathlib import Path
import json
import numpy as np
import time
import rembg
from PIL import Image

from diffusers import DiffusionPipeline, EulerAncestralDiscreteScheduler
from point_e.util.plotting import plot_point_cloud
from point_e.diffusion.configs import DIFFUSION_CONFIGS, diffusion_from_config
from point_e.diffusion.sampler import PointCloudSampler
from point_e.models.download import load_checkpoint
from point_e.models.configs import MODEL_CONFIGS, model_from_config
device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')

# Parts of this script are derived from a Point-E demo:
# https://github.com/openai/point-e/blob/main/point_e/examples/image2pointcloud.ipynb

# =============================================================================
# STABLE DIFFUSION
# =============================================================================
print("Creating Stable Diffusion model")
sd_pipe = DiffusionPipeline.from_pretrained(
    "stabilityai/stable-diffusion-xl-base-1.0",
    torch_dtype=torch.float16
)

# =============================================================================
# ZERO123
# =============================================================================
zero_123_pipe = DiffusionPipeline.from_pretrained(
    "sudo-ai/zero123plus-v1.1", custom_pipeline="sudo-ai/zero123plus-pipeline",
    torch_dtype=torch.float16
)
zero_123_pipe.scheduler = EulerAncestralDiscreteScheduler.from_config(
    zero_123_pipe.scheduler.config, timestep_spacing='trailing'
)

# =============================================================================
# POINT-E
# =============================================================================
print("Creating Point-E model")
base_name = 'base300M'
base_model = model_from_config(MODEL_CONFIGS[base_name], device)
base_model.eval()
base_diffusion = diffusion_from_config(DIFFUSION_CONFIGS[base_name])
upsampler_model = model_from_config(MODEL_CONFIGS['upsample'], device)
upsampler_model.eval()
upsampler_diffusion = diffusion_from_config(DIFFUSION_CONFIGS['upsample'])
base_model.load_state_dict(load_checkpoint(base_name, device))
upsampler_model.load_state_dict(load_checkpoint('upsample', device))
sampler = PointCloudSampler(
    device=device,
    models=[base_model, upsampler_model],
    diffusions=[base_diffusion, upsampler_diffusion],
    num_points=[1024, 4096 - 1024],
    aux_channels=['R', 'G', 'B'],
    guidance_scale=[3.0, 3.0],
)

prompt_dir = Path.home() / "prompts"
model_dir = Path.home() / "models"

print("Ready")

while True:
    prompt_files = list(prompt_dir.glob("*.txt"))
    if len(prompt_files) == 0:
        time.sleep(0.25)
        continue

    prompt_path = prompt_files[0]

    temp_img_path = prompt_dir / (prompt_path.stem + ".tmp.png")
    perm_img_path = model_dir / (prompt_path.stem + ".png")
    temp_path = prompt_dir / (prompt_path.stem + ".tmp.json")
    perm_path = model_dir / (prompt_path.stem + ".json")
    
    with prompt_path.open() as f:
        prompt = f.read().strip()

    # Set the prompt
    prompt = prompt + ", simple, plastic, 3D, against a plain background"

    # Generate the image
    print("Generating Stable Diffusion image")
    with torch.no_grad():
        generator = torch.manual_seed(0)
        sd_pipe.to("cuda")
        image = sd_pipe(prompt, num_inference_steps=50, generator=generator).images[0]
        sd_pipe.to("cpu")

    # Convert to PIL Image and save
    image.resize((64, 64)).save(temp_img_path)
    temp_img_path.rename(perm_img_path)

    print("Generating views with Zero123")

    zero_123_pipe.to("cuda")
    with torch.no_grad():
        tiled_image = zero_123_pipe(image, num_inference_steps=75).images[0]
    zero_123_pipe.to("cpu")
    view_size = 320
    cols = 2
    rows = 3
    num_views = cols * rows

    view_images = [tiled_image.crop((j*view_size, i*view_size, (j+1)*view_size, (i+1)*view_size)) for i in range(rows) for j in range(cols)]
    view_images = [rembg.remove(view.resize((128, 128))) for view in view_images]
    view_dir = Path("views")
    view_dir.mkdir(exist_ok=True)
    for i, view in enumerate(view_images):
        view.save(view_dir / f"view_img_{i}.png")

    # Generate a 3D model from the image
    print("Generating point cloud")

    # Produce a sample from the model.
    samples = None
    for x in tqdm(sampler.sample_batch_progressive(batch_size=num_views, model_kwargs=dict(images=view_images))):
        samples = x
        pc = sampler.output_to_point_clouds(samples)[0]
        rescaled_coords = (((pc.coords - pc.coords.mean(axis=0)) / (pc.coords.ptp(axis=0).max() / 2)).tolist())
        colors = [list(color) for color in zip(*(pc.channels[c].tolist() for c in ["R", "G", "B"]))]
        with temp_path.open("w") as f:
            json.dump({"coords": rescaled_coords, "colors": colors}, f)
        temp_path.rename(perm_path)

    fig = plot_point_cloud(pc, grid_size=3, fixed_bounds=((-0.75, -0.75, -0.75),(0.75, 0.75, 0.75)))
    print("Saving")
    fig.savefig("views_server.pdf")
    print("Saved")

    prompt_path.unlink()
