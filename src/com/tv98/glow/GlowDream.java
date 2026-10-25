package com.tv98.glow;

import android.graphics.drawable.GradientDrawable;
import android.service.dreams.DreamService;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Glow's ambient screensaver: a big clock, the weather, world headlines with their pictures, and what's on now on
 * your favourite channels. Everything drifts a few pixels every minute so nothing burns into the screen.
 */
public class GlowDream extends DreamService {
    private static final long STORY_MS = 12_000, DRIFT_MS = 60_000;

    private FrameLayout root;
    private LinearLayout stage;
    private TextView clock, date, weather, storyTitle, storyMeta, onNow;
    private ImageView storyImage;
    private final List<World.Story> stories = new ArrayList<>();
    private int storyIndex = -1, drift, skipped;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            clock.setText(DateFormat.format(Ui.is24h(GlowDream.this) ? "HH:mm" : "h:mm", now));
            date.setText(DateFormat.format("EEEE d MMMM", now));
            clock.postDelayed(this, 60_000 - now % 60_000 + 50);
        }
    };

    private final Runnable nextStory = new Runnable() {
        @Override
        public void run() {
            showNextStory();
            storyImage.postDelayed(this, STORY_MS);
        }
    };

    private final Runnable driftRun = new Runnable() {
        @Override
        public void run() {
            // Burn-in care: shift the whole layout around a small loop.
            drift = (drift + 1) % 8;
            float dx = (float) Math.cos(drift * Math.PI / 4) * Ui.dp(14), dy = (float) Math.sin(drift * Math.PI / 4) * Ui.dp(10);
            stage.animate().translationX(dx).translationY(dy).setDuration(4000).start();
            stage.postDelayed(this, DRIFT_MS);
            refreshOnNow();
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        Ui.init(this);

        root = new FrameLayout(this);
        root.setBackground(new Ui.Aurora());
        stage = new LinearLayout(this);
        stage.setOrientation(LinearLayout.HORIZONTAL);
        stage.setPadding(Ui.dp(56), Ui.dp(44), Ui.dp(56), Ui.dp(40));
        root.addView(stage, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Left: time, date, weather, and what's on now.
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        clock = Ui.text(this, 96, 0xEEFFFFFF, 300);
        clock.setShadowLayer(Ui.dp(24), 0, 0, 0x668FA8FF);
        date = Ui.text(this, 20, Ui.TEXT_DIM, 400);
        date.setPadding(Ui.dp(6), Ui.dp(4), 0, 0);
        weather = Ui.text(this, 26, Ui.TEXT, 300);
        weather.setPadding(Ui.dp(6), Ui.dp(18), 0, 0);
        left.addView(clock);
        left.addView(date);
        left.addView(weather);
        View spacer = new View(this);
        left.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        onNow = Ui.text(this, 14, 0xCCE6ECFF, 400);
        onNow.setLineSpacing(0, 1.35f);
        onNow.setPadding(Ui.dp(6), 0, Ui.dp(24), 0);
        left.addView(onNow);
        stage.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        // Right: a headline with its picture.
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        storyImage = new ImageView(this);
        storyImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        storyImage.setClipToOutline(true);
        GradientDrawable ib = new GradientDrawable();
        ib.setCornerRadius(Ui.dp(18));
        ib.setColor(0x221A2140);
        storyImage.setBackground(ib);
        card.addView(storyImage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(262)));
        storyTitle = Ui.text(this, 21, Ui.TEXT, 600);
        storyTitle.setMaxLines(3);
        storyTitle.setEllipsize(TextUtils.TruncateAt.END);
        storyTitle.setLineSpacing(0, 1.12f);
        storyTitle.setPadding(Ui.dp(4), Ui.dp(16), 0, 0);
        storyMeta = Ui.text(this, 13, Ui.TEXT_DIM, 400);
        storyMeta.setPadding(Ui.dp(4), Ui.dp(8), 0, 0);
        card.addView(storyTitle);
        card.addView(storyMeta);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Ui.dp(470), ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.CENTER_VERTICAL;
        stage.addView(card, clp);

        setContentView(root);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        tick.run();
        Weather.load(this, t -> weather.setText(t));
        Net.IO.execute(() -> {
            if (World.stale(this)) World.refresh(this);
            List<World.Story> top = World.section(World.cached(this), "__top");
            Guide.loadSaved(this);
            Net.MAIN.post(() -> {
                stories.clear();
                stories.addAll(top.subList(0, Math.min(30, top.size())));
                nextStory.run();
                refreshOnNow();
            });
        });
        stage.postDelayed(driftRun, DRIFT_MS);
    }

    /** Next headline that has a picture (stories without one are skipped here; this screen is about the pictures). */
    private void showNextStory() {
        if (stories.isEmpty()) return;
        for (int tries = 0; tries < stories.size(); tries++) {
            storyIndex = (storyIndex + 1) % stories.size();
            final World.Story s = stories.get(storyIndex);
            final int mine = storyIndex;
            World.image(s, url -> {
                if (mine != storyIndex) return;
                if (url.isEmpty()) { // no picture: move straight on (at most once round the list)
                    if (++skipped < stories.size()) showNextStory();
                    return;
                }
                skipped = 0;
                Net.bitmap(this, url, Ui.dp(470), bm -> {
                    if (bm == null || mine != storyIndex) return;
                    storyImage.animate().alpha(0f).setDuration(400).withEndAction(() -> {
                        storyImage.setImageBitmap(bm);
                        storyTitle.setText(s.title);
                        storyMeta.setText(s.source + "   ·   " + World.ago(s.at));
                        storyImage.animate().alpha(1f).setDuration(600).start();
                    }).start();
                });
            });
            return; // one lookup per turn; if it has no picture the next turn moves on
        }
    }

    /** "On now" for up to four favourites that have listings. */
    private void refreshOnNow() {
        Net.IO.execute(() -> {
            List<Channel> favs = Picks.favourites(this, FreeTv.cached(this));
            StringBuilder b = new StringBuilder();
            int n = 0;
            for (Channel ch : favs) {
                Guide.Prog p = Guide.now(ch);
                if (p == null) continue;
                if (n == 0) b.append("ON NOW\n");
                b.append(ch.name).append("  ·  ").append(p.title).append('\n');
                if (++n == 4) break;
            }
            final String text = b.toString().trim();
            Net.MAIN.post(() -> onNow.setText(text));
        });
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
        clock.removeCallbacks(tick);
        storyImage.removeCallbacks(nextStory);
        stage.removeCallbacks(driftRun);
        Net.trim();
    }
}
