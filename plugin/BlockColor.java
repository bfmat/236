package com.diffusioncraft.plugin;

import java.util.ArrayList;

public class BlockColor {
    double r, g, b;

    public BlockColor(double r, double g, double b) {
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public static BlockColor mean(ArrayList<BlockColor> colors) {
        BlockColor output = new BlockColor(0, 0, 0);
        double count = colors.size();
        for (BlockColor c : colors) {
            output.r += (c.r / count);
            output.g += (c.g / count);
            output.b += (c.b / count);
        }
        return output;
    }
}
