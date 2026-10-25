package com.tv98.glow;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Radio stations in the TV guide's own style, drawn as one view: number, name and logo on the left (like a channel
 * cell), and on the right what the station plays, or the song on air for the station that's playing.
 */
class RadioListView extends View {
    interface Listener {
        void onSelect(Radio.Station s, int number);
        void onPlay(Radio.Station s, int number);
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG), bold = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float rowH, gap, chanW, pad, radius, logoW, logoH;
    private List<Radio.Station> rows = new ArrayList<>();
    private int base, sel, top;
    private String playingUuid, song;
    private Listener listener;
    private final HashMap<String, Bitmap> logos = new HashMap<>();
    private final HashSet<String> asked = new HashSet<>();
    private final StringBuilder digits = new StringBuilder();
    private final Runnable applyDigits = this::jumpToTypedNumber;

    RadioListView(Context c) {
        super(c);
        setFocusable(true);
        setFocusableInTouchMode(true);
        rowH = Ui.dp(31);
        gap = Ui.dp(4);
        chanW = Ui.dp(330);
        pad = Ui.dp(10);
        radius = Ui.dp(3);
        logoW = Ui.dp(50);
        logoH = Ui.dp(19);
        text.setTextSize(Ui.dp(15));
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        bold.setTextSize(Ui.dp(15));
        bold.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    }

    void setListener(Listener l) {
        listener = l;
    }

    /** Stations of one section; {@code base} gives the numbers (base + 1, base + 2 …, shown as 0101, 0102 …). */
    void setStations(List<Radio.Station> stations, int base) {
        rows = stations;
        this.base = base;
        sel = 0;
        top = 0;
        changed();
    }

    void setPlaying(String uuid, String song) {
        playingUuid = uuid;
        this.song = song;
        invalidate();
    }

    /** Re-sends the highlighted station to the listener (its "on air" details changed). */
    void dispatchSelect() {
        if (listener != null && !rows.isEmpty()) listener.onSelect(rows.get(sel), base + sel + 1);
    }

    static String label(int number) {
        return String.format(java.util.Locale.ROOT, "%04d", number);
    }

    private int visibleRows() {
        int h = getHeight() > 0 ? getHeight() : Ui.dp(300);
        return Math.max(1, (int) (h / (rowH + gap)));
    }

    private void changed() {
        int vis = visibleRows();
        if (sel < top) top = sel;
        if (sel >= top + vis) top = sel - vis + 1;
        top = Math.max(0, Math.min(top, Math.max(0, rows.size() - vis)));
        invalidate();
        if (listener != null && !rows.isEmpty()) listener.onSelect(rows.get(sel), base + sel + 1);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        changed();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), rightX = chanW + gap;
        for (int i = 0; i < visibleRows() && top + i < rows.size(); i++) {
            int idx = top + i;
            Radio.Station s = rows.get(idx);
            float y = i * (rowH + gap);
            boolean hl = idx == sel;
            boolean on = s.uuid.equals(playingUuid);

            r.set(0, y, chanW, y + rowH);
            cell(c, hl);
            bold.setColor(hl ? GuideGridView.HILITE_TEXT : GuideGridView.CELL_TEXT);
            text.setColor(hl ? GuideGridView.HILITE_TEXT : GuideGridView.CELL_TEXT);
            float baseline = y + rowH - Ui.dp(9);
            c.drawText(label(base + idx + 1), pad, baseline, bold);
            float numW = bold.measureText("0000") + pad;
            float lw = drawLogo(c, s, y);
            fit(c, s.name, pad + numW, baseline, chanW - pad * 2 - numW - lw, text);

            r.set(rightX, y, w, y + rowH);
            cell(c, hl);
            String right = on ? "♫  On air" + (song == null || song.isEmpty() ? "" : "  ·  " + song) : Radio.describe(s);
            text.setColor(hl ? GuideGridView.HILITE_TEXT : on ? GuideGridView.HILITE : GuideGridView.DIM_TEXT);
            fit(c, right, rightX + pad, baseline, w - rightX - pad * 2, text);
        }
    }

    private void cell(Canvas c, boolean hl) {
        if (hl) {
            fill.setColor(GuideGridView.HILITE);
            c.drawRoundRect(r, radius, radius, fill);
            return;
        }
        fill.setColor(GuideGridView.CELL_EDGE);
        c.drawRoundRect(r, radius, radius, fill);
        fill.setColor(GuideGridView.CELL);
        c.drawRect(r.left, r.top + Ui.dp(2), r.right, r.bottom, fill);
    }

    private void fit(Canvas c, String s, float x, float baseline, float maxW, TextPaint p) {
        if (maxW <= Ui.dp(8) || s == null) return;
        CharSequence t = TextUtils.ellipsize(s, p, maxW, TextUtils.TruncateAt.END);
        c.drawText(t, 0, t.length(), x, baseline, p);
    }

    /** Logo at the right of the name cell, trimmed and centred on the text line like the TV guide's. */
    private float drawLogo(Canvas c, Radio.Station s, float y) {
        if (s.logo == null || s.logo.isEmpty()) return 0;
        Bitmap bm = logos.get(s.uuid);
        if (bm == null) {
            if (asked.add(s.uuid)) {
                final String key = s.uuid;
                Net.bitmap(getContext(), s.logo, Ui.dp(96), loaded -> {
                    if (loaded == null) return;
                    if (logos.size() > 120) logos.clear();
                    Bitmap art = GuideGridView.trim(loaded);
                    float k = Math.min(logoW / art.getWidth(), logoH / art.getHeight());
                    logos.put(key, Bitmap.createScaledBitmap(art, Math.max(1, Math.round(art.getWidth() * k)),
                            Math.max(1, Math.round(art.getHeight() * k)), true));
                    invalidate();
                });
            }
            return 0;
        }
        float right = chanW - Ui.dp(8), cx = right - logoW / 2, cy = y + rowH / 2 + Ui.dp(1);
        c.drawBitmap(bm, Math.round(cx - bm.getWidth() / 2f), Math.round(cy - bm.getHeight() / 2f), bitmapPaint);
        return logoW + Ui.dp(10);
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (rows.isEmpty()) return super.onKeyDown(code, e);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (sel > 0) sel--;
                changed();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (sel < rows.size() - 1) sel++;
                changed();
                return true;
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                sel = Math.max(0, sel - visibleRows());
                changed();
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                sel = Math.min(rows.size() - 1, sel + visibleRows());
                changed();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (listener != null) listener.onPlay(rows.get(sel), base + sel + 1);
                return true;
            default:
                if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9 && digits.length() < 4) {
                    digits.append((char) ('0' + code - KeyEvent.KEYCODE_0));
                    removeCallbacks(applyDigits);
                    postDelayed(applyDigits, 2000);
                    return true;
                }
                return super.onKeyDown(code, e);
        }
    }

    private void jumpToTypedNumber() {
        if (digits.length() == 0) return;
        int wanted = Integer.parseInt(digits.toString());
        digits.setLength(0);
        int idx = wanted - base - 1;
        if (idx >= 0 && idx < rows.size()) {
            sel = idx;
            top = Math.max(0, idx - visibleRows() / 2);
            changed();
        }
    }
}
