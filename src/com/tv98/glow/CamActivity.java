package com.tv98.glow;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A live cam full screen, in YouTube's own embedded player (no keys): ◀ ▶ next cam, OK opens it in the YouTube app,
 * BACK returns. The stream's latest picture shows until the video starts. It runs in its own process, so all of
 * WebView's memory goes when the viewer leaves and Home keeps its small footprint.
 */
public class CamActivity extends Activity {
    static final String EXTRA_CAM = "cam";
    private static final String BASE = "https://github.com/SashaMIT/glow-tv";
    /** WebView works the box hard (82-85 °C in ten minutes): past this, the cam closes rather than risk a restart. */
    private static final int STOP_AT = 86;

    private WebView web;
    private ImageView still;
    private LinearLayout caption, warning;
    private HeatWatch watch;
    private TextView title, detail;
    private int[] cams;
    private int at, skipped, direction = 1;
    private boolean pageLoaded;
    private final Runnable hideCaption = () -> caption.animate().alpha(0f).setDuration(400).start();
    private final Runnable play = this::play;
    private final Runnable heat = new Runnable() {
        @Override
        public void run() {
            int degrees = Vitals.readDegrees();
            if (degrees >= getIntent().getIntExtra("stop_at", STOP_AT)) {
                L.toast(CamActivity.this, "The box is too hot for live cams right now. Try again in a few minutes.", android.widget.Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            if (watch.warn(degrees, android.os.SystemClock.elapsedRealtime())) showWarning(degrees);
            caption.postDelayed(this, 20_000);
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        cams = list(getIntent().getIntExtra(EXTRA_CAM, 0));
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        web = new WebView(this);
        web.setBackgroundColor(0xFF000000);
        web.setFocusable(false);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return r.isForMainFrame(); // the player stays on this page
            }
        });
        web.addJavascriptInterface(new Page(), "Glow");
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        still = new ImageView(this);
        still.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(still, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        caption = new LinearLayout(this);
        caption.setOrientation(LinearLayout.VERTICAL);
        caption.setPadding(Ui.dp(28), Ui.dp(40), Ui.dp(28), Ui.dp(20));
        caption.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                new int[] {0xE60B0E1C, 0x000B0E1C}));
        title = Ui.text(this, 26, Ui.TEXT, 600);
        detail = Ui.text(this, 15, Ui.TEXT_DIM, 400);
        caption.addView(title);
        caption.addView(detail);
        root.addView(caption, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        warning = new LinearLayout(this);
        warning.setOrientation(LinearLayout.VERTICAL);
        warning.setPadding(Ui.dp(28), Ui.dp(22), Ui.dp(28), Ui.dp(22));
        android.graphics.drawable.GradientDrawable wb = new android.graphics.drawable.GradientDrawable();
        wb.setColor(0xF00B0E1C);
        wb.setCornerRadius(Ui.dp(16));
        wb.setStroke(Ui.dp(1), 0x40FFFFFF);
        warning.setBackground(wb);
        warning.setVisibility(View.GONE);
        FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(Ui.dp(680), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(warning, wlp);
        watch = new HeatWatch(getIntent().getIntExtra("warn_at", HeatWatch.WARN_AT), getIntent().getLongExtra("warn_after", HeatWatch.WARN_AFTER));
        setContentView(root);
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        show();
        play();
        caption.postDelayed(heat, 20_000);
    }

    /** The cams ◀ ▶ walks through: those live now, with the one asked for even if it isn't. */
    private int[] list(int start) {
        int[] order = Cams.order(this);
        int live = 0;
        boolean has = false;
        int[] out = new int[order.length];
        for (int i : order) {
            if (Cams.live(this, i) || i == start) {
                has |= i == start;
                if (i == start) at = live;
                out[live++] = i;
            }
        }
        return has ? java.util.Arrays.copyOf(out, live) : new int[] {start};
    }

    private void show() {
        String[] r = WmCams.CAMS[cams[at]];
        title.setText(r[1]);
        detail.setText(r[2] + "  ·  " + r[3] + "      ◀ ▶  " + L.t("next cam") + "      OK  " + L.t("open in YouTube"));
        caption.removeCallbacks(hideCaption);
        caption.animate().cancel();
        caption.setAlpha(1f);
        still.setImageDrawable(null);
        still.setBackground(new SourceArt(r[1], 0xFF2C5FB8, Ui.dp(14)));
        still.setVisibility(View.VISIBLE);
        Net.image(still, "https://i.ytimg.com/vi/" + Cams.id(this, cams[at]) + "/mqdefault_live.jpg", Ui.dp(480), null);
    }

    private void play() {
        String id = Cams.id(this, cams[at]);
        if (pageLoaded) {
            web.evaluateJavascript("go('" + id + "')", null);
            return;
        }
        pageLoaded = true;
        web.loadDataWithBaseURL(BASE, "<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<style>html,body,#p{margin:0;width:100%;height:100%;background:#000;overflow:hidden}</style></head><body><div id=p></div>"
                + "<script>var pl,first='" + id + "';function go(i){if(pl&&pl.loadVideoById)pl.loadVideoById(i);else first=i}"
                + "function onYouTubeIframeAPIReady(){pl=new YT.Player('p',{width:'100%',height:'100%',videoId:first,"
                + "playerVars:{autoplay:1,controls:0,disablekb:1,fs:0,iv_load_policy:3,modestbranding:1,playsinline:1,rel:0,origin:'https://github.com'},"
                + "events:{onReady:function(e){e.target.playVideo()},onError:function(e){Glow.failed(e.data)},"
                + "onStateChange:function(e){if(e.data==1)Glow.playing()}}})}</script>"
                + "<script src='https://www.youtube.com/iframe_api'></script></body></html>", "text/html", "UTF-8", null);
    }

    private void move(int step) {
        direction = step;
        skipped = 0;
        step(step);
    }

    private void step(int step) {
        at = (at + step + cams.length) % cams.length;
        show();
        // Held ◀ ▶ walks the captions; the stream is only asked for once the remote rests.
        web.removeCallbacks(play);
        web.postDelayed(play, 400);
    }

    /**
     * The box has run hot for a few minutes: offer the two cams that play in Glow's own player (about 40 % CPU and
     * 70 °C, against YouTube's 200 % and 83 °C), or a stop. BACK keeps watching.
     */
    private void showWarning(int degrees) {
        android.util.Log.i("GlowPerf", "cam heat heads-up at " + degrees + " C");
        warning.removeAllViews();
        TextView head = Ui.text(this, 21, Ui.TEXT, 600);
        head.setText(L.f("The box has been at {n} °C for a few minutes", degrees));
        TextView body = Ui.text(this, 15, Ui.TEXT_DIM, 400);
        body.setText(L.t("YouTube cams work it hard. Mecca and Medina play in Glow's own player and stay much cooler."));
        body.setLineSpacing(0, 1.15f);
        body.setPadding(0, Ui.dp(6), 0, Ui.dp(16));
        warning.addView(head);
        warning.addView(body);
        LinearLayout buttons = new LinearLayout(this);
        View first = null;
        for (String[] b : new String[][] {{"mecca", "Watch Mecca"}, {"medina", "Watch Medina"}, {"", "Stop"}}) {
            final int cam = b[0].isEmpty() ? -1 : Cams.index(b[0]);
            if (!b[0].isEmpty() && (cam < 0 || Cams.direct(b[0]) == null)) continue;
            TextView t = Ui.text(this, 16, Ui.TEXT, 600);
            t.setText(L.t(b[1]));
            t.setGravity(Gravity.CENTER);
            t.setPadding(Ui.dp(20), Ui.dp(10), Ui.dp(20), Ui.dp(10));
            t.setFocusable(true);
            t.setClickable(true);
            t.setBackground(Ui.glass(Ui.dp(10), false));
            t.setOnFocusChangeListener((v, has) -> v.setBackground(Ui.glass(Ui.dp(10), has)));
            t.setOnClickListener(v -> {
                if (cam >= 0) IntelActivity.openCam(this, cam);
                finish();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(12);
            buttons.addView(t, lp);
            if (first == null) first = t;
        }
        TextView back = Ui.text(this, 13, Ui.TEXT_DIM, 400);
        back.setText(L.t("BACK keeps watching"));
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setPadding(Ui.dp(8), 0, 0, 0);
        buttons.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        warning.addView(buttons);
        warning.setVisibility(View.VISIBLE);
        if (first != null) first.requestFocus();
    }

    /** What the player page tells Glow (these two calls are all it can reach). */
    private final class Page {
        @JavascriptInterface public void playing() {
            runOnUiThread(() -> {
                skipped = 0;
                android.util.Log.i("GlowPerf", "cam playing: " + WmCams.CAMS[cams[at]][0]);
                still.setVisibility(View.GONE);
                caption.removeCallbacks(hideCaption);
                caption.postDelayed(hideCaption, 4000);
            });
        }

        /** The stream can't play here (ended, or its owner doesn't allow embedding): on to the next. */
        @JavascriptInterface public void failed(int code) {
            runOnUiThread(() -> {
                android.util.Log.i("GlowPerf", "cam " + WmCams.CAMS[cams[at]][0] + " won't embed: " + code);
                if (++skipped >= cams.length || cams.length == 1) {
                    IntelActivity.openInYouTube(CamActivity.this, cams[at]);
                    finish();
                } else {
                    step(direction);
                }
            });
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (warning.getVisibility() == View.VISIBLE) { // the heads-up has the remote until it's answered
            if (code == KeyEvent.KEYCODE_BACK) {
                if (down) warning.setVisibility(View.GONE);
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                if (down) move(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                if (down) move(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (down) {
                    IntelActivity.openInYouTube(this, cams[at]);
                    finish();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) { // bring the caption back
                    caption.removeCallbacks(hideCaption);
                    caption.animate().cancel();
                    caption.setAlpha(1f);
                    caption.postDelayed(hideCaption, 4000);
                }
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isFinishing()) finish(); // never left playing behind something else
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        caption.removeCallbacks(heat);
        web.removeCallbacks(play);
        web.destroy();
        // This process only exists for the player: leaving frees all of it at once.
        Net.MAIN.postDelayed(() -> android.os.Process.killProcess(android.os.Process.myPid()), 300);
    }
}
