package com.diffusioncraft.plugin.commands;

import com.diffusioncraft.plugin.DiffusionCraftPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class RateLimitCommand implements CommandExecutor {

    DiffusionCraftPlugin plugin;

    public RateLimitCommand(DiffusionCraftPlugin pl) {
        plugin = pl;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
        plugin.allowedFrequencyMillis = Long.valueOf(args[0]) * 1000L;
        sender.sendMessage("Set frequency to " + plugin.allowedFrequencyMillis + " milliseconds");
        return false;
    }
}
