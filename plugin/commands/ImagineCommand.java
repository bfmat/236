package com.diffusioncraft.plugin.commands;

import com.diffusioncraft.plugin.APIRequest;
import com.diffusioncraft.plugin.DiffusionCraftPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;

public class ImagineCommand implements CommandExecutor {

    DiffusionCraftPlugin plugin;

    public ImagineCommand(DiffusionCraftPlugin pl) {
        plugin = pl;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (args.length > 0) {
                if (!plugin.lastUseTimes.containsKey(player.getUniqueId())) {
                    plugin.lastUseTimes.put(player.getUniqueId(), 0L);
                }
                long timeSinceLast = System.currentTimeMillis() - plugin.lastUseTimes.get(player.getUniqueId());
                if (timeSinceLast < (plugin.allowedFrequencyMillis)) {
                    player.sendMessage("Please wait " + (plugin.allowedFrequencyMillis - timeSinceLast) / 1000 + " more seconds");
                } else {
                    plugin.lastUseTimes.put(player.getUniqueId(), System.currentTimeMillis());
                    makeImage(player, player.getEyeLocation(), player.getWorld(), String.join(" ", args));
                }
            } else {
                player.sendMessage("Usage: /imagine <prompt>");
            }
        }
        return false;
    }

    private void makeImage(Player player, Location playerEyeLoc, World world, String desc) {

        player.sendMessage("Generating \"" + desc + "\"...");

        try {
            APIRequest job = new APIRequest(desc, plugin, player.getEyeLocation(), player);
            plugin.pool.execute(job);

        } catch (IOException e) {
            player.sendMessage("Internal plugin error");
        }
    }
}
