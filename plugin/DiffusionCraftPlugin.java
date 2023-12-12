package com.diffusioncraft.plugin;

import com.diffusioncraft.plugin.commands.ImagineCommand;
import com.diffusioncraft.plugin.commands.RateLimitCommand;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;


public class DiffusionCraftPlugin extends JavaPlugin {

    public LinkedBlockingQueue<SetBlockRecord> setBlockRecords = new LinkedBlockingQueue<>();
    public ExecutorService pool;
    private BukkitTask task;
    public HashMap<UUID, Long> lastUseTimes = new HashMap<>();

    public long allowedFrequencyMillis = 60000L;

    @Override
    public void onEnable() {
        this.getCommand("imagine").setExecutor(new ImagineCommand(this));
        getLogger().info("Added the 'imagine' command.");
        this.getCommand("setratelimit").setExecutor(new RateLimitCommand(this));
        getLogger().info("Added the 'setratelimit' command.");

        pool = Executors.newCachedThreadPool();

        task = new BukkitRunnable() {
            @Override
            public void run() {
                for (int i = 0; i < 1024; i++) {
                    SetBlockRecord record = setBlockRecords.poll();
                    if (record == null) {
                        break;
                    } else {
                        Material material = Material.matchMaterial("minecraft:" + record.blockId);
                        Block block = record.location.getWorld().getBlockAt(record.location);
                        if (material == null) {
                            block.setType(Material.AIR);
                        } else {
                            block.setType(material);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0, 1L);
    }
}