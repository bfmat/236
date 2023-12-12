package com.diffusioncraft.plugin;

import org.bukkit.Location;

public class SetBlockRecord {
    String blockId;
    Location location;

    public SetBlockRecord(String id, Location loc) {
        blockId = id;
        location = loc;
    }
}
