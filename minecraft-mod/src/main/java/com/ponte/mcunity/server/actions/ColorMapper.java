package com.ponte.mcunity.server.actions;

import java.util.HashMap;
import java.util.Map;

/**
 * Mapeia cores hex para blocos coloridos do Minecraft (concrete/wool/terracotta).
 * Usa distância euclidiana RGB para escolher a cor mais próxima das 16 cores vanilla.
 */
public class ColorMapper {
    private static final String[] COLOR_NAMES = {
            "white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow",
            "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink"
    };

    // RGB das 16 cores de concrete vanilla (aproximado)
    private static final int[][] COLOR_RGB = {
            {233, 236, 236}, // white
            {179, 179, 179}, // light_gray
            {100, 100, 100}, // gray
            {25, 25, 25},    // black
            {114, 71, 40},   // brown
            {160, 39, 34},   // red
            {226, 121, 32},  // orange
            {252, 197, 39},  // yellow
            {112, 185, 25},  // lime
            {58, 91, 18},    // green
            {21, 119, 136},  // cyan
            {37, 137, 198},  // light_blue
            {44, 46, 143},   // blue
            {100, 32, 156},  // purple
            {169, 48, 159},  // magenta
            {214, 101, 143}, // pink
    };

    private final Map<String, String> cache = new HashMap<>();

    public String closestBlock(String hex, String material) {
        String key = hex.toLowerCase() + "|" + material;
        if (cache.containsKey(key)) return cache.get(key);

        int[] target = hexToRgb(hex);
        int bestIdx = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < COLOR_RGB.length; i++) {
            double d = dist(target, COLOR_RGB[i]);
            if (d < bestDist) {
                bestDist = d;
                bestIdx = i;
            }
        }
        String colorName = COLOR_NAMES[bestIdx];
        // material: concrete, wool, terracotta, concrete_powder, glazed_terracotta
        String blockId = "minecraft:" + colorName + "_" + material;
        // fallback para concrete se material inválido
        cache.put(key, blockId);
        return blockId;
    }

    private int[] hexToRgb(String hex) {
        hex = hex.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() == 3) hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return new int[]{r, g, b};
        } catch (Exception e) {
            return new int[]{255, 255, 255};
        }
    }

    private double dist(int[] a, int[] b) {
        double dr = a[0] - b[0], dg = a[1] - b[1], db = a[2] - b[2];
        return dr * dr + dg * dg + db * db;
    }
}
