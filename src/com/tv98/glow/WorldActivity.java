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

/** World headlines by region and topic, with the selected story's summary underneath. */
public class WorldActivity extends Activity {
    private static final long REFRESH_MS = 10 * 60 * 1000L;

    private List<World.Story> all = new ArrayList<>();
    private List<World.Story> shown = new ArrayList<>();
    private final List<String[]> sections = new ArrayList<>();
    private ListView sectionList;
    private GridView stories;
    private TextView heading, updated, detailTitle, detailBody, detailMeta;
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
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(Ui.dp(200), ViewGroup.LayoutParams.MATCH_PARENT);
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
        right.setPadding(Ui.dp(24), 0, 0, Ui.dp(24));
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
        stories.setOnFocusChangeListener((v, has) -> {
            light(has ? (Tile) stories.getSelectedView() : null);
            if (has) detail(stories.getSelectedItemPosition());
        });
        right.addView(stories, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable pbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xCC1A2140, 0xCC0B0E1C});
        pbg.setCornerRadius(Ui.dp(16));
        pbg.setStroke(Math.max(1, Ui.dp(0.75f)), 0x408FA8FF);
        panel.setBackground(pbg);
        panel.setPadding(Ui.dp(20), Ui.dp(14), Ui.dp(20), Ui.dp(14));
        detailTitle = Ui.text(this, 16, Ui.TEXT, 600);
        detailTitle.setMaxLines(2);
        detailTitle.setEllipsize(TextUtils.TruncateAt.END);
        detailBody = Ui.text(this, 13, 0xCCF2F4FF, 400);
        detailBody.setMaxLines(3);
        detailBody.setEllipsize(TextUtils.TruncateAt.END);
        detailBody.setLineSpacing(0, 1.15f);
        detailBody.setPadding(0, Ui.dp(8), 0, Ui.dp(8));
        detailMeta = Ui.text(this, 11, Ui.TEXT_DIM, 400);
        panel.addView(detailTitle);
        panel.addView(detailBody);
        panel.addView(detailMeta);
        right.addView(panel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(132)));

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
        World.Story s = shown.get(pos);
        detailTitle.setText(s.title);
        detailBody.setText(s.snippet.isEmpty() ? "No summary for this story." : s.snippet);
        String threat = s.threat.isEmpty() || s.threat.equals("UNSPECIFIED") ? "" : "   ·   " + s.threat.toLowerCase() + " concern";
        detailMeta.setText(s.source + "   ·   " + World.ago(s.at) + threat);
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
                t = new Tile(WorldActivity.this, 0, 58);
                t.setFocusable(false);
                t.setClickable(false);
                t.setLift(1.012f);
                t.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(58)));
                LinearLayout line = new LinearLayout(WorldActivity.this);
                line.setGravity(Gravity.CENTER_VERTICAL);
                line.setPadding(Ui.dp(16), 0, Ui.dp(16), 0);
                View dot = new View(WorldActivity.this);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(8), Ui.dp(8));
                dlp.rightMargin = Ui.dp(14);
                line.addView(dot, dlp);
                LinearLayout words = new LinearLayout(WorldActivity.this);
                words.setOrientation(LinearLayout.VERTICAL);
                TextView title = Ui.text(WorldActivity.this, 15, Ui.TEXT, 400);
                title.setSingleLine();
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
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(World.color(s));
            line.getChildAt(0).setBackground(dot);
            LinearLayout words = (LinearLayout) line.getChildAt(1);
            ((TextView) words.getChildAt(0)).setText(s.title);
            ((TextView) words.getChildAt(1)).setText(s.source + (s.moreSources > 0 ? " +" + s.moreSources + " more" : "")
                    + "   ·   " + World.ago(s.at) + (s.alert ? "   ·   ALERT" : ""));
            return t;
        }
    }
}
