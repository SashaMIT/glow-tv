package com.tv98.glow;

/**
 * When a long hot stretch deserves a heads-up: {@code warnAt} °C or more for {@code after} ms without a break, said
 * once. Cooling well below the line (5 °C) starts over, so a second hot stretch later gets its own heads-up.
 * No screen code: the cam player feeds it readings and shows the panel.
 */
final class HeatWatch {
    /** YouTube cams sit at 82-85 °C; Glow's own player stays near 70. */
    static final int WARN_AT = 80;
    static final long WARN_AFTER = 4 * 60_000L;

    private final int warnAt;
    private final long after;
    private long hotSince = -1;
    private boolean warned;

    HeatWatch(int warnAt, long after) {
        this.warnAt = warnAt;
        this.after = after;
    }

    /** A reading ({@code degrees} ≤ 0 when it can't be read); true when it's time for the heads-up. */
    boolean warn(int degrees, long now) {
        if (degrees < warnAt) {
            hotSince = -1;
            if (degrees > 0 && degrees < warnAt - 5) warned = false;
            return false;
        }
        if (hotSince < 0) hotSince = now;
        if (warned || now - hotSince < after) return false;
        warned = true;
        return true;
    }

    /** How long it's been hot, in ms (0 when it isn't). */
    long hotFor(long now) {
        return hotSince < 0 ? 0 : now - hotSince;
    }
}
