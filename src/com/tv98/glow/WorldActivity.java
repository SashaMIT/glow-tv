package com.tv98.glow;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * World headlines by region and topic: sections on the left, stories with their pictures in the middle, and
 * the highlighted story on the right (picture, summary, a QR code to read it on your phone). OK on a story
 * tunes to a live news channel that covers that part of the world.
 */
public class WorldActivity extends Activity {
    private static final long REFRESH_MS = 10 * 60 * 1000L;

    private List<World.Story> all = new ArrayList<>();
    private List<World.Story> shown = new ArrayList<>();
    private final List<String[]> sections = new ArrayList<>();
    private ListView sectionList;
    private GridView stories;
    private TextView heading, updated, detailTitle, detailBody, detailMeta, detailLive;
    private android.widget.ImageView detailImage, detailQr;
    private TextView detailQrNote;
    private int detailGen;
    private Tile lit;
    private final Runnable apply = this::applySection;
    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            load(true);
            heading.postDelayed(this, REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);

        LinearLayout root = new LinearLayout(this);
        root.setBackground(new Ui.Aurora());
        root.setPadding(Ui.dp(32), Ui.dp(28), Ui.dp(24), 0);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setBackground(Ui.glass(Ui.dp(18), false));
        left.setPadding(Ui.dp(10), Ui.dp(16), Ui.dp(10), Ui.dp(10));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(Ui.dp(172), ViewGroup.LayoutParams.MATCH_PARENT);
        llp.bottomMargin = Ui.dp(28);
        root.addView(left, llp);
        TextView t = Ui.label(this, "World");
        t.setPadding(Ui.dp(12), 0, 0, Ui.dp(10));
        left.addView(t);

        sectionList = new ListView(this);
        sectionList.setDivider(null);
        sectionList.setSelector(Ui.glass(Ui.dp(10), true));
        sectionList.setVerticalScrollBarEnabled(false);
        sectionList.setAdapter(new SectionAdapter());
        sectionList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                sectionList.removeCallbacks(apply);
                sectionList.postDelayed(apply, 200);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        sectionList.setOnItemClickListener((p, v, pos, id) -> stories.requestFocus());
        left.addView(sectionList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        TextView credit = Ui.text(this, 10, Ui.TEXT_DIM, 400);
        credit.setText("Headlines: worldmonitor.app");
        credit.setPadding(Ui.dp(12), Ui.dp(8), 0, Ui.dp(2));
        left.addView(credit);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(Ui.dp(20), 0, 0, Ui.dp(20));
        root.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        heading = Ui.text(this, 24, Ui.TEXT, 300);
        heading.setPadding(Ui.dp(6), 0, 0, 0);
        right.addView(heading);
        updated = Ui.text(this, 12, Ui.TEXT_DIM, 400);
        updated.setPadding(Ui.dp(8), Ui.dp(6), 0, 0);
        right.addView(updated);

        stories = new GridView(this);
        stories.setNumColumns(1);
        stories.setVerticalSpacing(Ui.dp(8));
        stories.setPadding(0, Ui.dp(14), Ui.dp(6), Ui.dp(14));
        stories.setClipToPadding(false);
        stories.setClipChildren(false);
        stories.setSelector(new ColorDrawable(0));
        stories.setVerticalScrollBarEnabled(false);
        stories.setAdapter(new StoryAdapter());
        stories.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                light(stories.hasFocus() ? (Tile) v : null);
                detail(pos);
            }
            @Override public void onNothingSelected(AdapterView<?> p) { light(null); }
        });
        stories.setOnItemClickListener((p, v, pos, id) -> { if (pos < shown.size()) watchLive(shown.get(pos)); });
        stories.setOnFocusChangeListener((v, has) -> {
            light(has ? (Tile) stories.getSelectedView() : null);
            if (has) detail(stories.getSelectedItemPosition());
        });
        right.addView(stories, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // The highlighted story, on the right: picture, headline, summary, where to watch it live, and a QR code.
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable pbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xCC1A2140, 0xCC0B0E1C});
        pbg.setCornerRadius(Ui.dp(18));
        pbg.setStroke(Math.max(1, Ui.dp(0.75f)), 0x408FA8FF);
        panel.setBackground(pbg);
        panel.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(14), Ui.dp(12));
        detailImage = new android.widget.ImageView(this);
        detailImage.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        detailImage.setClipToOutline(true);
        GradientDrawable ib = new GradientDrawable();
        ib.setCornerRadius(Ui.dp(12));
        ib.setColor(0x221A2140);
        detailImage.setBackground(ib);
        panel.addView(detailImage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(158)));
        detailTitle = Ui.text(this, 16, Ui.TEXT, 600);
        detailTitle.setMaxLines(3);
        detailTitle.setEllipsize(TextUtils.TruncateAt.END);
        detailTitle.setLineSpacing(0, 1.1f);
        detailTitle.setPadding(0, Ui.dp(10), 0, 0);
        detailBody = Ui.text(this, 12.5f, 0xCCF2F4FF, 400);
        detailBody.setMaxLines(3);
        detailBody.setEllipsize(TextUtils.TruncateAt.END);
        detailBody.setLineSpacing(0, 1.15f);
        detailBody.setPadding(0, Ui.dp(7), 0, Ui.dp(6));
        detailMeta = Ui.text(this, 11, Ui.TEXT_DIM, 400);
        panel.addView(detailTitle);
        panel.addView(detailBody);
        panel.addView(detailMeta);
        View spacer = new View(this);
        panel.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        LinearLayout foot = new LinearLayout(this);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout footText = new LinearLayout(this);
        footText.setOrientation(LinearLayout.VERTICAL);
        detailLive = Ui.text(this, 13, Ui.ACCENT, 600);
        detailQrNote = Ui.text(this, 11, Ui.TEXT_DIM, 400);
        detailQrNote.setText("Scan to read the full story on your phone");
        detailQrNote.setPadding(0, Ui.dp(6), Ui.dp(8), 0);
        footText.addView(detailLive);
        footText.addView(detailQrNote);
        foot.addView(footText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        detailQr = new android.widget.ImageView(this);
        detailQr.setBackgroundColor(0xFFFFFFFF);
        detailQr.setPadding(Ui.dp(4), Ui.dp(4), Ui.dp(4), Ui.dp(4));
        foot.addView(detailQr, new LinearLayout.LayoutParams(Ui.dp(76), Ui.dp(76)));
        panel.addView(foot);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(Ui.dp(330), ViewGroup.LayoutParams.MATCH_PARENT);
        plp.leftMargin = Ui.dp(18);
        plp.bottomMargin = Ui.dp(24);
        root.addView(panel, plp);

        setContentView(root);
        for (String[] s : World.SECTIONS) sections.add(s);
        heading.setText("Top stories");
        updated.setText("Loading headlines…");
        load(false);
        sectionList.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        heading.postDelayed(refresher, REFRESH_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        heading.removeCallbacks(refresher);
    }

    private void load(boolean force) {
        Net.IO.execute(() -> {
            List<World.Story> cached = World.cached(this);
            if (!cached.isEmpty()) Net.MAIN.post(() -> show(cached));
            if (force || cached.isEmpty() || World.stale(this)) {
                World.refresh(this);
                final List<World.Story> fresh = World.cached(this);
                Net.MAIN.post(() -> {
                    if (fresh.isEmpty()) updated.setText("Couldn't reach World Monitor. Check the internet connection.");
                    else if (fresh != all) show(fresh);
                });
            }
        });
    }

    private void show(List<World.Story> stories) {
        all = stories;
        long newest = 0;
        for (World.Story s : stories) newest = Math.max(newest, s.at);
        updated.setText(stories.size() + " headlines from " + countSources(stories) + " sources   ·   latest " + World.ago(newest));
        ((BaseAdapter) sectionList.getAdapter()).notifyDataSetChanged();
        applySection();
    }

    private static int countSources(List<World.Story> stories) {
        java.util.Set<String> s = new java.util.HashSet<>();
        for (World.Story st : stories) s.add(st.source);
        return s.size();
    }

    private void applySection() {
        int pos = Math.max(0, sectionList.getSelectedItemPosition());
        String[] sec = sections.get(Math.min(pos, sections.size() - 1));
        shown = World.section(all, sec[0]);
        heading.setText(sec[1] + "   ·   " + shown.size());
        lit = null;
        ((BaseAdapter) stories.getAdapter()).notifyDataSetChanged();
        if (!shown.isEmpty()) {
            stories.setSelection(0);
            detail(0);
        } else {
            detailTitle.setText(all.isEmpty() ? "" : "Nothing here right now.");
            detailBody.setText("");
            detailMeta.setText("");
        }
    }

    private void detail(int pos) {
        if (pos < 0 || pos >= shown.size()) return;
        final World.Story s = shown.get(pos);
        final int gen = ++detailGen;
        detailTitle.setText(s.title);
        detailBody.setText(s.snippet.isEmpty() ? "No summary for this story." : s.snippet);
        String threat = s.threat.isEmpty() || s.threat.equals("UNSPECIFIED") ? "" : "   ·   " + s.threat.toLowerCase() + " concern";
        detailMeta.setText(s.source + (s.moreSources > 0 ? " +" + s.moreSources + " more" : "") + "   ·   " + World.ago(s.at) + threat);
        detailImage.setImageDrawable(null);
        detailImage.setBackground(new SourceArt(s.source, World.color(s), Ui.dp(12)));
        World.image(s, url -> {
            if (gen != detailGen) return;
            if (!url.isEmpty()) Net.image(detailImage, url, Ui.dp(330), null);
        });
        boolean link = s.link != null && s.link.startsWith("http");
        detailQr.setVisibility(link ? View.VISIBLE : View.GONE);
        detailQrNote.setVisibility(link ? View.VISIBLE : View.GONE);
        if (link) detailQr.setImageBitmap(RemoteActivity.qrBitmap(s.link, Ui.dp(68)));
        detailLive.setText("");
        Net.IO.execute(() -> {
            final Channel ch = World.liveFor(this, s);
            Net.MAIN.post(() -> {
                if (gen == detailGen) detailLive.setText(ch == null ? "" : "OK  ▶  Watch live on " + ch.name);
            });
        });
    }

    /** OK on a story: tune to a news channel covering that region, with the guide's News list to zap through. */
    private void watchLive(final World.Story s) {
        Net.IO.execute(() -> {
            final Channel ch = World.liveFor(this, s);
            Net.MAIN.post(() -> {
                if (ch == null || isFinishing()) return;
                List<Channel> list = new ArrayList<>();
                GuideLineup l = GuideLineup.current();
                if (l != null) for (GuideLineup.Section sec : l.main) if (sec.name.equals("News")) list.addAll(sec.channels);
                if (!list.contains(ch)) list.add(0, ch);
                Xtream.playlist = list;
                startActivity(new android.content.Intent(this, PlayerActivity.class).putExtra("index", list.indexOf(ch)));
            });
        });
    }

    private void light(Tile t) {
        if (lit == t) return;
        if (lit != null) lit.setLit(false);
        lit = t;
        if (t != null) t.setLit(true);
    }

    private final class SectionAdapter extends BaseAdapter {
        @Override public int getCount() { return sections.size(); }
        @Override public Object getItem(int i) { return sections.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            TextView tv = convert instanceof TextView ? (TextView) convert : Ui.text(WorldActivity.this, 15, Ui.TEXT, 400);
            String[] s = sections.get(i);
            int n = all.isEmpty() ? 0 : World.section(all, s[0]).size();
            tv.setText(n > 0 && s[0].equals("__alerts") ? s[1] + "  (" + n + ")" : s[1]);
            tv.setAlpha(all.isEmpty() || n > 0 ? 1f : 0.4f);
            tv.setSingleLine();
            tv.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(10), Ui.dp(10));
            return tv;
        }
    }

    /** One headline: alert dot, title, then source and age. */
    private final class StoryAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            Tile t;
            if (convert instanceof Tile) {
                t = (Tile) convert;
                if (t == lit) light(null);
            } else {
                t = new Tile(WorldActivity.this, 0, 66);
                t.setFocusable(false);
                t.setClickable(false);
                t.setLift(1.012f);
                t.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(66)));
                LinearLayout line = new LinearLayout(WorldActivity.this);
                line.setGravity(Gravity.CENTER_VERTICAL);
                line.setPadding(Ui.dp(8), 0, Ui.dp(14), 0);
                android.widget.ImageView thumb = new android.widget.ImageView(WorldActivity.this);
                thumb.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                thumb.setClipToOutline(true);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(88), Ui.dp(50));
                dlp.rightMargin = Ui.dp(12);
                line.addView(thumb, dlp);
                LinearLayout words = new LinearLayout(WorldActivity.this);
                words.setOrientation(LinearLayout.VERTICAL);
                TextView title = Ui.text(WorldActivity.this, 14, Ui.TEXT, 400);
                title.setMaxLines(2);
                title.setLineSpacing(0, 1.05f);
                title.setEllipsize(TextUtils.TruncateAt.END);
                TextView meta = Ui.text(WorldActivity.this, 11, Ui.TEXT_DIM, 400);
                meta.setPadding(0, Ui.dp(5), 0, 0);
                words.addView(title);
                words.addView(meta);
                line.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                t.card.addView(line);
            }
            World.Story s = shown.get(i);
            LinearLayout line = (LinearLayout) t.card.getChildAt(0);
            // Placeholder in the story's colour until (or unless) its picture arrives.
            final android.widget.ImageView thumb = (android.widget.ImageView) line.getChildAt(0);
            thumb.setBackground(new SourceArt(s.source, World.color(s), Ui.dp(8)));
            thumb.setImageDrawable(null);
            final String link = s.link;
            thumb.setTag(link);
            World.image(s, url -> {
                if (link == null || !link.equals(thumb.getTag()) || url.isEmpty()) return;
                Net.image(thumb, url, Ui.dp(120), null);
            });
            LinearLayout words = (LinearLayout) line.getChildAt(1);
            ((TextView) words.getChildAt(0)).setText(s.title);
            ((TextView) words.getChildAt(1)).setText(s.source + (s.moreSources > 0 ? " +" + s.moreSources + " more" : "")
                    + "   ·   " + World.ago(s.at) + (s.alert ? "   ·   ALERT" : ""));
            return t;
        }
    }
}
