package com.tv98.glow;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The player's decisions, with no screen code: the picture's size for each picture mode, how far back rewinding
 * can go, the sleep timer's steps and what an audio or subtitle track is called. PlayerActivity asks; tests check.
 */
final class PlayerRules {
    static final int FIT = 0, FILL = 1, STRETCH = 2, ZOOM = 3;
    /** Sleep timer steps in minutes (▶ while watching); 0 is off. */
    static final int[] SLEEP_STEPS = {0, 30, 60, 90, 120};
    /** Less kept than this isn't worth moving playback onto. */
    static final long MIN_KEPT_MS = 20_000;

    private static final Pattern CAPTION = Pattern.compile("(?i)(?:cc|caption)[ _-]?(\\d+)");

    private PlayerRules() {}

    /**
     * The video's size on a {@code screenW} × {@code screenH} screen, {width, height}: Fit shows the whole picture
     * at its real shape, Fill covers the screen trimming the edges, Stretch fills it, Zoom is a third bigger than Fit
     * (4:3 shows inside a 16:9 frame, black bars round the picture). Null when there's nothing to size.
     */
    static int[] fit(int w, int h, float pixelRatio, int screenW, int screenH, int mode) {
        if (w <= 0 || h <= 0 || screenW <= 0 || screenH <= 0) return null;
        float video = w * pixelRatio / h, screen = (float) screenW / screenH;
        int vw = screenW, vh = screenH;
        switch (mode) {
            case FILL:
                if (video > screen) vw = Math.round(vh * video);
                else vh = Math.round(vw / video);
                break;
            case STRETCH:
                break;
            case ZOOM:
                if (video > screen) vh = Math.round(vw / video);
                else vw = Math.round(vh * video);
                vw = Math.round(vw * 1.33f);
                vh = Math.round(vh * 1.33f);
                break;
            default:
                if (video > screen) vh = Math.round(vw / video);
                else vw = Math.round(vh * video);
        }
        return new int[] {vw, vh};
    }

    /**
     * Where rewinding {@code step} ms from {@code pos} (ms into the live window, or into what Glow has kept when
     * {@code shifted}) lands, or -1 at the edge: a live window's first segments may already be gone when they'd
     * load, so it stops 8 s from the start (1 s in what's kept).
     */
    static long rewindTarget(long pos, long step, boolean shifted) {
        long earliest = shifted ? 1_000 : 8_000;
        long target = Math.max(earliest, pos - step);
        if (target >= pos - 1_000 || (!shifted && pos - step < earliest)) return -1;
        return target;
    }

    /**
     * Where to start in what Glow has kept of a channel ({@code kept} ms, {@code ahead} of it already buffered but not
     * shown) to play from {@code step} ms before the picture now; -1 when not enough is kept.
     */
    static long keptStart(long kept, long ahead, long step) {
        long start = kept - Math.max(0, ahead) - step;
        return kept < MIN_KEPT_MS || start < 0 ? -1 : start;
    }

    /** ▶ on the sleep timer: the next step up from {@code leftMs} left (minutes of {@code unit} ms), 0 after the last. */
    static int nextSleep(long leftMs, long unit) {
        for (int m : SLEEP_STEPS) if (m * unit > Math.max(0, leftMs) + unit / 2) return m;
        return 0;
    }

    /** A track's name in the menu: "Captions 2" for a stream's "caption_2", its own label, or the language in English. */
    static String trackName(String label, String language) {
        String l = label == null ? "" : label.trim();
        Matcher cc = CAPTION.matcher(l);
        if (cc.matches()) return "Captions " + cc.group(1);
        if (!l.isEmpty()) return Character.toUpperCase(l.charAt(0)) + l.substring(1);
        if (language == null || language.isEmpty() || language.equals("und")) return "Unknown";
        String name = new Locale(language).getDisplayLanguage(Locale.ENGLISH);
        return name.isEmpty() ? language : name;
    }
}
