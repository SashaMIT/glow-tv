package com.tv98.glow;

import android.app.Activity;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A channel as a list row: number, logo, name, then country / genre, and an "offline" tag.
 * Used by the channel browser's list view and by search. The row is the Tile; this holder is its tag.
 */
final class ChannelRow {
    final Tile tile;
    private final Activity a;
    private final TextView num, initials, name, detail, status, pickTag;
    private final ImageView logo;

    ChannelRow(Activity a) {
        this.a = a;
        tile = new Tile(a, 0, 54);
        tile.setFocusable(false);
        tile.setClickable(false);
        tile.setLift(1.015f);
        tile.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(54)));
        tile.setTag(this);

        LinearLayout line = new LinearLayout(a);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(Ui.dp(12), 0, Ui.dp(18), 0);
        num = Ui.text(a, 15, Ui.ACCENT, 300);
        num.setGravity(Gravity.END);
        line.addView(num, new LinearLayout.LayoutParams(Ui.dp(44), ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout art = new FrameLayout(a);
        logo = new ImageView(a);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        art.addView(logo);
        initials = Ui.text(a, 13, Ui.TEXT_DIM, 600);
        initials.setGravity(Gravity.CENTER);
        art.addView(initials);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Ui.dp(64), Ui.dp(38));
        alp.setMargins(Ui.dp(16), 0, Ui.dp(16), 0);
        line.addView(art, alp);

        name = Ui.text(a, 16, Ui.TEXT, 400);
        name.setSingleLine();
        name.setEllipsize(TextUtils.TruncateAt.END);
        line.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        pickTag = Ui.text(a, 10, Ui.ACCENT, 600);
        pickTag.setText("TOP PICK");
        pickTag.setLetterSpacing(0.12f);
        pickTag.setPadding(Ui.dp(12), 0, 0, 0);
        line.addView(pickTag);
        status = Ui.text(a, 11, 0xFFFFB4A8, 600);
        status.setText("OFFLINE");
        status.setLetterSpacing(0.1f);
        status.setPadding(Ui.dp(12), 0, 0, 0);
        line.addView(status);
        detail = Ui.text(a, 12, Ui.TEXT_DIM, 400);
        detail.setSingleLine();
        detail.setMaxWidth(Ui.dp(330));
        detail.setEllipsize(TextUtils.TruncateAt.END);
        detail.setPadding(Ui.dp(14), 0, 0, 0);
        line.addView(detail);
        tile.card.addView(line);
    }

    /** Reuses a recycled row when it is one, otherwise makes a new one. */
    static ChannelRow of(Activity a, View convert) {
        return convert instanceof Tile && ((Tile) convert).getTag() instanceof ChannelRow
                ? (ChannelRow) ((Tile) convert).getTag() : new ChannelRow(a);
    }

    /** "★  Name" with a gold star. */
    static CharSequence star(String name) {
        android.text.SpannableString s = new android.text.SpannableString("★  " + name);
        s.setSpan(new android.text.style.ForegroundColorSpan(0xFFFFD27A), 0, 1, 0);
        return s;
    }

    /** showCountry: for mixed-country lists (news, sports, search) put the country before the genre. */
    View bind(Channel ch, boolean offline, boolean showCountry, boolean pick) {
        int n = GuideLineup.numberOf(ch);
        num.setText(n > 0 ? String.valueOf(n) : "");
        boolean fav = Picks.isFavourite(a, ch);
        name.setText(fav ? star(ch.name) : ch.name);
        pickTag.setVisibility(pick && !fav ? View.VISIBLE : View.GONE);
        String country = showCountry ? Channel.countryName(ch.country) : null;
        String genre = ch.group;
        if (country != null && "News".equals(genre)) genre = null;
        Guide.Prog onNow = Guide.now(ch);
        if (onNow != null) genre = "▶ " + onNow.title; // what's on beats the genre label
        detail.setText(country == null ? (genre == null ? "" : genre) : genre == null ? country : country + "  ·  " + genre);
        status.setVisibility(offline ? View.VISIBLE : View.GONE);
        tile.card.setAlpha(offline ? 0.55f : 1f);
        initials.setText(Ui.initials(ch.name));
        initials.setVisibility(View.GONE);
        Net.image(logo, ch.logo, Ui.dp(64), () -> initials.setVisibility(View.VISIBLE));
        return tile;
    }
}
