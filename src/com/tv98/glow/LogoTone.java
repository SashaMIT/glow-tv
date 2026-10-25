package com.tv98.glow;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.HashMap;
import java.util.Map;

/**
 * Whether a channel logo reads better on a black card than a white one: logos drawn mostly in white or very light
 * colours (white lettering) go on black, everything else on white. A logo with its own solid background follows
 * that background's brightness, so its square blends into the card.
 */
final class LogoTone {
    private static final Map<String, Boolean> DARK = new HashMap<>();

    private LogoTone() {}

    static boolean wantsDark(String url, Bitmap bm) {
        Boolean known = DARK.get(url);
        if (known != null) return known;
        boolean dark = measure(bm);
        DARK.put(url, dark);
        return dark;
    }

    private static boolean measure(Bitmap bm) {
        int w = bm.getWidth(), h = bm.getHeight();
        if (w < 2 || h < 2) return false;
        // Corners all solid and alike: the logo brings its own background.
        int[] corners = {bm.getPixel(1, 1), bm.getPixel(w - 2, 1), bm.getPixel(1, h - 2), bm.getPixel(w - 2, h - 2)};
        boolean solid = true;
        for (int c : corners) if (Color.alpha(c) < 230 || Math.abs(lum(c) - lum(corners[0])) > 0.12) solid = false;
        if (solid) return lum(corners[0]) < 0.35;

        // Otherwise look at the inked pixels: mostly light means white lettering, which disappears on white.
        int step = Math.max(1, (int) Math.sqrt(w * (double) h / 3000));
        int ink = 0, light = 0;
        int[] row = new int[w];
        for (int y = 0; y < h; y += step) {
            bm.getPixels(row, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x += step) {
                int c = row[x];
                if (Color.alpha(c) < 160) continue;
                ink++;
                if (lum(c) > 0.82) light++;
            }
        }
        return ink > 0 && light > ink * 0.55;
    }

    private static double lum(int c) {
        return (0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)) / 255.0;
    }
}
