package com.hytalecraft.world;

import com.hypixel.hytale.logger.HytaleLogger;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps RGB colors to the closest matching Hytale block names using CIE-LAB color space.
 * All block names are verified against the official block_types_dump.txt.
 */
public class HytaleBlockPalette {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Block name -> average RGB color
    private static final Map<String, int[]> PALETTE = new HashMap<>();

    // Cache: RGB -> block name
    private static final Map<Integer, String> COLOR_CACHE = new HashMap<>();

    static {
        // === Soil_Clay (13 verified colors) ===
        addBlock("Soil_Clay", 135, 95, 70);            // natural clay base
        addBlock("Soil_Clay_Black", 30, 30, 35);
        addBlock("Soil_Clay_Blue", 45, 65, 130);
        addBlock("Soil_Clay_Cyan", 55, 120, 125);
        addBlock("Soil_Clay_Green", 60, 100, 55);
        addBlock("Soil_Clay_Grey", 90, 90, 95);
        addBlock("Soil_Clay_Lime", 100, 155, 70);
        addBlock("Soil_Clay_Ocean", 50, 80, 110);
        addBlock("Soil_Clay_Orange", 185, 105, 45);
        addBlock("Soil_Clay_Pink", 185, 120, 130);
        addBlock("Soil_Clay_Purple", 90, 50, 120);
        addBlock("Soil_Clay_Red", 145, 50, 45);
        addBlock("Soil_Clay_White", 210, 210, 215);
        addBlock("Soil_Clay_Yellow", 195, 175, 60);

        // === Soil_Clay_Smooth (12 verified colors) ===
        addBlock("Soil_Clay_Smooth_Black", 25, 25, 30);
        addBlock("Soil_Clay_Smooth_Blue", 40, 55, 120);
        addBlock("Soil_Clay_Smooth_Cyan", 48, 110, 115);
        addBlock("Soil_Clay_Smooth_Green", 52, 90, 48);
        addBlock("Soil_Clay_Smooth_Grey", 80, 80, 85);
        addBlock("Soil_Clay_Smooth_Lime", 90, 145, 62);
        addBlock("Soil_Clay_Smooth_Orange", 175, 95, 38);
        addBlock("Soil_Clay_Smooth_Pink", 175, 110, 120);
        addBlock("Soil_Clay_Smooth_Purple", 80, 42, 110);
        addBlock("Soil_Clay_Smooth_Red", 135, 42, 38);
        addBlock("Soil_Clay_Smooth_White", 200, 200, 205);
        addBlock("Soil_Clay_Smooth_Yellow", 185, 165, 50);

        // === Cloth_Block_Wool (20 verified colors incl. Light variants) ===
        addBlock("Cloth_Block_Wool_Black", 20, 20, 25);
        addBlock("Cloth_Block_Wool_Blue", 35, 50, 140);
        addBlock("Cloth_Block_Wool_Blue_Light", 90, 130, 170);
        addBlock("Cloth_Block_Wool_Cyan", 40, 115, 120);
        addBlock("Cloth_Block_Wool_Cyan_Light", 95, 155, 160);
        addBlock("Cloth_Block_Wool_Gray", 75, 75, 80);
        addBlock("Cloth_Block_Wool_Gray_Light", 145, 145, 150);
        addBlock("Cloth_Block_Wool_Green", 48, 85, 42);
        addBlock("Cloth_Block_Wool_Green_Light", 100, 155, 70);
        addBlock("Cloth_Block_Wool_Orange", 195, 110, 40);
        addBlock("Cloth_Block_Wool_Orange_Light", 220, 160, 80);
        addBlock("Cloth_Block_Wool_Pink", 195, 125, 135);
        addBlock("Cloth_Block_Wool_Pink_Light", 220, 170, 175);
        addBlock("Cloth_Block_Wool_Purple", 85, 45, 115);
        addBlock("Cloth_Block_Wool_Purple_Light", 140, 90, 165);
        addBlock("Cloth_Block_Wool_Red", 155, 45, 40);
        addBlock("Cloth_Block_Wool_Red_Light", 195, 95, 85);
        addBlock("Cloth_Block_Wool_White", 225, 225, 230);
        addBlock("Cloth_Block_Wool_Yellow", 210, 185, 55);
        addBlock("Cloth_Block_Wool_Yellow_Light", 230, 215, 110);

        // === Rock types (base solid blocks) ===
        addBlock("Rock_Stone", 128, 128, 130);
        addBlock("Rock_Basalt", 55, 55, 60);
        addBlock("Rock_Marble", 220, 218, 215);
        addBlock("Rock_Sandstone", 195, 175, 130);
        addBlock("Rock_Sandstone_Red", 175, 110, 70);
        addBlock("Rock_Sandstone_White", 215, 205, 185);
        addBlock("Rock_Slate", 85, 90, 100);
        addBlock("Rock_Shale", 100, 95, 85);
        addBlock("Rock_Quartzite", 190, 185, 175);
        addBlock("Rock_Calcite", 200, 195, 180);
        addBlock("Rock_Chalk", 215, 210, 200);
        addBlock("Rock_Aqua", 80, 120, 115);
        addBlock("Rock_Volcanic", 45, 40, 40);
        addBlock("Rock_Salt", 220, 215, 210);
        addBlock("Rock_Ice", 170, 210, 230);
        addBlock("Rock_Ice_Permafrost", 145, 175, 195);
        addBlock("Rock_Magma_Cooled", 50, 35, 30);
        addBlock("Rock_Bedrock", 35, 30, 35);
        addBlock("Rock_Stone_Cobble", 115, 115, 115);

        // === Rock Crystal Blocks (8 verified colors) ===
        addBlock("Rock_Crystal_Blue_Block", 50, 80, 200);
        addBlock("Rock_Crystal_Cyan_Block", 50, 180, 190);
        addBlock("Rock_Crystal_Green_Block", 50, 170, 70);
        addBlock("Rock_Crystal_Pink_Block", 200, 90, 140);
        addBlock("Rock_Crystal_Purple_Block", 120, 50, 180);
        addBlock("Rock_Crystal_Red_Block", 190, 40, 45);
        addBlock("Rock_Crystal_White_Block", 230, 235, 240);
        addBlock("Rock_Crystal_Yellow_Block", 220, 200, 50);

        // === Rock Gem Blocks ===
        addBlock("Rock_Gem_Diamond", 130, 210, 225);
        addBlock("Rock_Gem_Emerald", 40, 170, 75);
        addBlock("Rock_Gem_Ruby", 175, 30, 45);
        addBlock("Rock_Gem_Sapphire", 30, 55, 175);
        addBlock("Rock_Gem_Topaz", 210, 170, 45);
        addBlock("Rock_Gem_Voidstone", 40, 20, 50);
        addBlock("Rock_Gem_Zephyr", 180, 215, 200);

        // === Rock Brick Smooth variants (for polished looks) ===
        addBlock("Rock_Stone_Brick_Smooth", 140, 140, 140);
        addBlock("Rock_Basalt_Brick_Smooth", 50, 48, 55);
        addBlock("Rock_Gold_Brick_Smooth", 220, 190, 50);
        addBlock("Rock_Volcanic_Brick_Smooth", 40, 35, 38);

        // === Wood Planks (11 verified types) ===
        addBlock("Wood_Blackwood_Planks", 35, 28, 25);
        addBlock("Wood_Darkwood_Planks", 60, 40, 28);
        addBlock("Wood_Deadwood_Planks", 140, 130, 110);
        addBlock("Wood_Drywood_Planks", 160, 140, 105);
        addBlock("Wood_Goldenwood_Planks", 185, 150, 70);
        addBlock("Wood_Greenwood_Planks", 85, 95, 55);
        addBlock("Wood_Hardwood_Planks", 155, 120, 75);
        addBlock("Wood_Lightwood_Planks", 195, 175, 140);
        addBlock("Wood_Redwood_Planks", 120, 55, 35);
        addBlock("Wood_Softwood_Planks", 170, 135, 90);
        addBlock("Wood_Tropicalwood_Planks", 145, 100, 55);

        // === Soil / natural ===
        addBlock("Soil_Dirt", 105, 80, 55);
        addBlock("Soil_Dirt_Dry", 130, 105, 70);
        addBlock("Soil_Dirt_Cold", 85, 70, 60);
        addBlock("Soil_Sand", 210, 195, 155);
        addBlock("Soil_Sand_Red", 175, 110, 70);
        addBlock("Soil_Sand_White", 225, 220, 200);
        addBlock("Soil_Sand_Ashen", 140, 135, 125);
        addBlock("Soil_Grass", 95, 145, 60);
        addBlock("Soil_Grass_Dry", 150, 140, 75);
        addBlock("Soil_Grass_Cold", 75, 110, 80);
        addBlock("Soil_Gravel", 130, 125, 120);
        addBlock("Soil_Mud", 75, 60, 45);
        addBlock("Soil_Snow", 240, 245, 250);
        addBlock("Soil_Ash", 95, 90, 88);
        addBlock("Soil_Pebbles", 140, 135, 125);

        LOGGER.atInfo().log("[HytaleBlockPalette] Loaded %d blocks in palette", PALETTE.size());
    }

    private static void addBlock(String name, int r, int g, int b) {
        PALETTE.put(name, new int[]{r, g, b});
    }

    /**
     * Gets the closest matching Hytale block name for an RGB color.
     */
    public static String getClosestBlock(int rgb) {
        if (COLOR_CACHE.containsKey(rgb)) {
            return COLOR_CACHE.get(rgb);
        }

        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        double[] labTarget = rgbToLab(r, g, b);

        String closestBlock = "Rock_Stone";
        double minDistance = Double.MAX_VALUE;

        for (Map.Entry<String, int[]> entry : PALETTE.entrySet()) {
            int[] blockRgb = entry.getValue();
            double[] labBlock = rgbToLab(blockRgb[0], blockRgb[1], blockRgb[2]);

            // CIE76 Delta-E
            double distance = Math.sqrt(
                    Math.pow(labTarget[0] - labBlock[0], 2) +
                    Math.pow(labTarget[1] - labBlock[1], 2) +
                    Math.pow(labTarget[2] - labBlock[2], 2)
            );

            if (distance < minDistance) {
                minDistance = distance;
                closestBlock = entry.getKey();
            }
        }

        COLOR_CACHE.put(rgb, closestBlock);
        return closestBlock;
    }

    /**
     * Converts RGB to CIE-LAB color space.
     */
    private static double[] rgbToLab(int r, int g, int b) {
        double var_R = r / 255.0;
        double var_G = g / 255.0;
        double var_B = b / 255.0;

        if (var_R > 0.04045) var_R = Math.pow((var_R + 0.055) / 1.055, 2.4);
        else var_R = var_R / 12.92;
        if (var_G > 0.04045) var_G = Math.pow((var_G + 0.055) / 1.055, 2.4);
        else var_G = var_G / 12.92;
        if (var_B > 0.04045) var_B = Math.pow((var_B + 0.055) / 1.055, 2.4);
        else var_B = var_B / 12.92;

        var_R *= 100;
        var_G *= 100;
        var_B *= 100;

        double X = var_R * 0.4124 + var_G * 0.3576 + var_B * 0.1805;
        double Y = var_R * 0.2126 + var_G * 0.7152 + var_B * 0.0722;
        double Z = var_R * 0.0193 + var_G * 0.1192 + var_B * 0.9505;

        double var_X = X / 95.047;
        double var_Y = Y / 100.000;
        double var_Z = Z / 108.883;

        if (var_X > 0.008856) var_X = Math.pow(var_X, 1.0 / 3.0);
        else var_X = (7.787 * var_X) + (16.0 / 116.0);
        if (var_Y > 0.008856) var_Y = Math.pow(var_Y, 1.0 / 3.0);
        else var_Y = (7.787 * var_Y) + (16.0 / 116.0);
        if (var_Z > 0.008856) var_Z = Math.pow(var_Z, 1.0 / 3.0);
        else var_Z = (7.787 * var_Z) + (16.0 / 116.0);

        double L = (116 * var_Y) - 16;
        double a = 500 * (var_X - var_Y);
        double b_lab = 200 * (var_Y - var_Z);

        return new double[]{L, a, b_lab};
    }

    public static void clearCache() {
        COLOR_CACHE.clear();
    }
}
