package com.diffusioncraft.plugin;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import javax.imageio.ImageIO;

public class APIRequest implements Runnable {
    String prompt;
    DiffusionCraftPlugin plugin;
    Location playerLoc;
    Player player;
    double coordScale = 24;

    Path promptPath;
    Path modelPath;
    Path imagePath;

    public APIRequest(String pr, DiffusionCraftPlugin pl, Location loc, Player pla) throws IOException {
        prompt = pr;
        plugin = pl;
        playerLoc = loc;
        player = pla;

        String uuid = UUID.randomUUID().toString();
        promptPath = Paths.get(System.getProperty("user.home"), "prompts", uuid + ".txt");
        modelPath = Paths.get(System.getProperty("user.home"), "models", uuid + ".json");
        imagePath = Paths.get(System.getProperty("user.home"), "models", uuid + ".png");
    }

    private static class JsonData {
        double[][] colors;
        double[][] coords;
    }

    private SetBlockRecord makeRecord(BlockColor color, Location blockLoc) {

        // Pick the closest color to the mean
        String minBlock;
        int minDist = 100000000;
        for (int k = 0; k < ColorMapping.colors.length; k++) {
            int[] refRgba = ColorMapping.colors[k];
            int redErr = refRgba[0] - (int)(color.r * 255);
            int greenErr = refRgba[1] - (int)(color.g * 255);
            int blueErr = refRgba[2] - (int)(color.b * 255);
            int dist = (redErr * redErr) + (greenErr * greenErr) + (blueErr * blueErr);
            if (dist < minDist) {
                minDist = dist;
                minBlock = ColorMapping.blockNames[k];
            }
        }

        return new SetBlockRecord(minBlock, blockLoc);
    }

    @Override
    public void run() {

        // Write the prompt to a file and then wait
        try {
            Files.write(promptPath, prompt.getBytes(), StandardOpenOption.CREATE);
        } catch (IOException exc) {
            player.sendMessage("Internal error: " + exc.getMessage());
        }

        Gson gson = new Gson();
        Type type = new TypeToken<JsonData>(){}.getType();

        while (true) {

            HashMap<BlockPos, ArrayList<BlockColor>> newBlocks = new HashMap<>();
            ArrayList<SetBlockRecord> newRecords = new ArrayList<>();

            // IMAGE HANDLING
            // If there is a PNG image available, load it
            try {
                // Load the image
                File imageFile = new File(imagePath.toString());
                BufferedImage image = ImageIO.read(imageFile);

                // Get image dimensions
                int width = image.getWidth();
                int height = image.getHeight();

                // Iterate over each pixel and set blocks below the player
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int pixel = image.getRGB(x, y);

                        // Extract RGB values and normalize them
                        double r = ((double)((pixel >> 16) & 0xff)) / 255;
                        double g = ((double)((pixel >> 8) & 0xff)) / 255;
                        double b = ((double)(pixel & 0xff)) / 255;
                        BlockColor color = new BlockColor(r, g, b);

                        Location blockLoc = new Location(
                            playerLoc.getWorld(),
                            playerLoc.getX() + x - ((double)width / 2),
                            playerLoc.getY() - ((double)coordScale / 2),
                            playerLoc.getZ() + y - ((double)height / 2)
                        );

                        SetBlockRecord record = makeRecord(color, blockLoc);
                        plugin.setBlockRecords.add(record);
                    }
                }

                imageFile.delete();
            } catch (IOException ignored) {}

            // 3D MODEL HANDLING
            // If there is a JSON model provided, load it
            try {
                JsonReader reader = new JsonReader(new FileReader(modelPath.toString()));
                JsonData jsonData = gson.fromJson(reader, type);

                ArrayList<ColoredPoint> coloredPoints = new ArrayList<>();
                for (int i = 0; i < jsonData.coords.length; i++) {
                    double[] coords = jsonData.coords[i];
                    double[] color = jsonData.colors[i];
                    // Transpose because z is up for point-e
                    ColoredPoint point = new ColoredPoint(coords[0] * coordScale, coords[2] * coordScale, coords[1] * coordScale, color[0], color[1], color[2]);
                    coloredPoints.add(point);
                }
                reader.close();

                // Take said points and average together the colors of the points in each block area,
                // using a sparse dictionary representation so as to avoid storing a million empty boxes
                // for a structure of volume 100^3
                for (ColoredPoint pt : coloredPoints) {
                    BlockPos intPos = new BlockPos((int)pt.x, (int)pt.y, (int)pt.z);
                    if (!newBlocks.keySet().contains(intPos)) {
                        newBlocks.put(intPos, new ArrayList<>());
                    }
                    newBlocks.get(intPos).add(new BlockColor(pt.r, pt.g, pt.b));
                }

                // Add the block colors to the async generation list
                for (BlockPos relPos : newBlocks.keySet()) {
                    BlockColor color = BlockColor.mean(newBlocks.get(relPos));
                    Location blockLoc = new Location(
                        playerLoc.getWorld(),
                        playerLoc.getX() + relPos.x,
                        playerLoc.getY() + relPos.y + coordScale,
                        playerLoc.getZ() + relPos.z
                    );
                    SetBlockRecord record = makeRecord(color, blockLoc);
                    plugin.setBlockRecords.add(record);
                    newRecords.add(record);
                }

            } catch (IOException ignored) {}

            // Sleep the thread
            try {
                Thread.sleep(5000);
            } catch (InterruptedException exc) {
                player.sendMessage("Internal error: " + exc.getMessage());
            }

            // Air out all the blocks created on this round
            if (Files.exists(promptPath)) {
                for (SetBlockRecord record : newRecords) {
                    SetBlockRecord airRecord = new SetBlockRecord("air", record.location);
                    plugin.setBlockRecords.add(airRecord);
                }
            } else {
                // If the prompt is deleted, end this thread
                break;
            }
        }
    }
}
