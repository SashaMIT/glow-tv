package com.tv98.glow;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Full-screen live player. Up/Down (or CH+/CH−) change channel, OK shows channel info.
 *
 * Plays with ExoPlayer, which uses Android's own networking and copes with HTTPS, encrypted HLS and
 * ad-stitched streams that the box's built-in Allwinner player fails on. If a link doesn't draw a
 * picture within a few seconds, it rotates through the channel's other links (see {@link Sources})
 * and finally the built-in player, then remembers whichever worked.
 */
public class PlayerActivity extends Activity {
    /** How long one link gets to show a picture before the next is tried. */
    private static final long ATTEMPT_MS = 7_000;
    private static final String DEFAULT_UA = "Mozilla/5.0 (Linux; Android 10; Android TV) AppleWebKit/537.36 Glow/1.0";

    private FrameLayout root;
    private SurfaceView surface;
    private androidx.media3.exoplayer.upstream.DefaultBandwidthMeter meter;
    private DefaultTrackSelector tracks;
    /** Fast channel changes: each channel starts capped at 2.5 Mbps (a small first chunk), lifted after the picture appears. */
    private static final int START_CAP_BPS = 2_500_000;
    private final Runnable uncap = () -> {
        if (tracks != null) tracks.setParameters(tracks.buildUponParameters().setMaxVideoBitrate(Integer.MAX_VALUE));
    };
    private VideoView builtIn;
    private ExoPlayer exo;
    private View curtain;
    private ProgressBar spinner;
    private LinearLayout osd;
    private ImageView osdLogo;
    private TextView osdNum, osdName, osdNote, osdNow;
    private Xtream.Account acct;
    private int index;
    private List<Sources.Attempt> attempts = new ArrayList<>();
    private int attempt;
    private int generation;
    private boolean playing;
    /** This attempt already got its extra time (its stream answered, so it's alive, just slow to show a picture). */
    private boolean extended;
    /** True when this channel was reached with ▲▼ / digits: don't jump out to YouTube uninvited. */
    private boolean zapping;
    private String pendingYoutube;
    private int lastIndex = -1;
    private final StringBuilder digits = new StringBuilder();
    private final Runnable applyDigits = this::goToTypedNumber;
    /** Source lists prepared for the neighbouring channels while the current one plays. */
    private final Map<String, List<Sources.Attempt>> warm = new java.util.concurrent.ConcurrentHashMap<>();
    private long playStart;
    /** What's on screen now, for the phone remote. */
    static volatile String nowPlaying;
    static volatile Channel nowChannel;
    /** Link checks for the current channel: attempt index -> reachable. */
    private final Map<Integer, Boolean> probes = new java.util.concurrent.ConcurrentHashMap<>();
    private final Runnable hideOsd = () -> osd.animate().alpha(0f).setDuration(300).start();
    private final Runnable nextAttempt = () -> tryAttempt(attempt + 1);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        acct = Xtream.load(this);
        if (Xtream.playlist.isEmpty()) {
            finish();
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        index = Math.min(getIntent().getIntExtra("index", 0), Xtream.playlist.size() - 1);

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        surface = new SurfaceView(this);
        root.addView(surface, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        builtIn = new VideoView(this);
        builtIn.setVisibility(View.GONE);
        builtIn.setOnInfoListener((mp, what, extra) -> {
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) onPicture();
            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) spinner.setVisibility(View.VISIBLE);
            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) spinner.setVisibility(View.GONE);
            return false;
        });
        builtIn.setOnErrorListener((mp, what, extra) -> {
            if (!playing && builtIn.getVisibility() == View.VISIBLE) next();
            return true;
        });
        root.addView(builtIn, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        // Covers the last frame of the previous channel until the new one draws.
        curtain = new View(this);
        curtain.setBackgroundColor(0xFF000000);
        root.addView(curtain, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        spinner = new ProgressBar(this);
        root.addView(spinner, new FrameLayout.LayoutParams(Ui.dp(48), Ui.dp(48), Gravity.CENTER));

        osd = new LinearLayout(this);
        osd.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable panel = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xE01A2140, 0xE00B0E1C});
        panel.setCornerRadius(Ui.dp(18));
        panel.setStroke(Math.max(1, Ui.dp(0.75f)), 0x408FA8FF);
        osd.setBackground(panel);
        osd.setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(24), Ui.dp(12));
        osdLogo = new ImageView(this);
        osdLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        osd.addView(osdLogo, new LinearLayout.LayoutParams(Ui.dp(64), Ui.dp(44)));
        osdNum = Ui.text(this, 30, Ui.ACCENT, 300);
        osdNum.setPadding(Ui.dp(14), 0, Ui.dp(14), 0);
        osd.addView(osdNum);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        osdName = Ui.text(this, 20, Ui.TEXT, 600);
        osdNote = Ui.text(this, 12, Ui.TEXT_DIM, 400);
        osdNote.setPadding(0, Ui.dp(5), 0, 0);
        words.addView(osdName);
        osdNow = Ui.text(this, 13, 0xDDF2F4FF, 400);
        osdNow.setPadding(0, Ui.dp(4), 0, 0);
        osdNow.setMaxWidth(Ui.dp(560));
        osdNow.setSingleLine();
        osdNow.setEllipsize(android.text.TextUtils.TruncateAt.END);
        words.addView(osdNow);
        words.addView(osdNote);
        osd.addView(words);
        FrameLayout.LayoutParams olp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        olp.setMargins(Ui.dp(40), 0, 0, Ui.dp(36));
        root.addView(osd, olp);

        setContentView(root);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Xtream.playlist.isEmpty()) return;
        createExo();
        play(index, false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        nowPlaying = null;
        nowChannel = null;
        generation++;
        root.removeCallbacks(nextAttempt);
        if (builtIn != null) builtIn.stopPlayback();
        saveBandwidth();
        root.removeCallbacks(prepareStandby);
        releaseStandby();
        if (exo != null) {
            exo.release();
            exo = null;
        }
    }

    /** Remembers the connection speed ExoPlayer measured, so the next channel can start at a fitting quality. */
    private void saveBandwidth() {
        if (meter == null) return;
        long est = meter.getBitrateEstimate();
        if (est > 500_000) getSharedPreferences("player", MODE_PRIVATE).edit().putLong("bandwidth", est).apply();
    }

    private void createExo() {
        // Picture quality: Android reports this box's screen as 1280x720 (only the menus are drawn at that size; the
        // HDMI output is 3840x2160 and video has its own hardware layer), and ExoPlayer by default caps video to the
        // reported screen size, which held 1080p channels at 720p or below. Clear that cap; allow up to 1080p
        // (enough for every free stream here and comfortable for the decoder and 1 GB of memory).
        androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection.Factory adaptive =
                new androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection.Factory(
                        4_000,   // step up after 4 s of good throughput (default 10 s)
                        25_000,  // step down only when the buffer is really at risk
                        25_000,
                        0.8f);   // use 80 % of the measured bandwidth (default 70 %)
        if (meter == null) {
            // Start each channel from the connection speed measured last time (kept between channels and restarts),
            // at 60 % so the first segment still arrives quickly; ExoPlayer then steps up within seconds.
            long known = getSharedPreferences("player", MODE_PRIVATE).getLong("bandwidth", 0);
            long initial = known > 0 ? Math.max(1_500_000L, Math.min(12_000_000L, known * 6 / 10)) : 1_500_000L;
            meter = new androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.Builder(this).setInitialBitrateEstimate(initial).build();
        }
        tracks = newSelector();
        exo = buildPlayer(tracks, false);
        attachMain(exo);
        exo.setVideoSurfaceView(surface);
        exo.setPlayWhenReady(true);
    }

    private DefaultTrackSelector newSelector() {
        androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection.Factory adaptive =
                new androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection.Factory(
                        4_000,   // step up after 4 s of good throughput (default 10 s)
                        25_000,  // step down only when the buffer is really at risk
                        25_000,
                        0.8f);   // use 80 % of the measured bandwidth (default 70 %)
        DefaultTrackSelector sel = new DefaultTrackSelector(this, adaptive);
        sel.setParameters(sel.buildUponParameters().clearViewportSizeConstraints().setMaxVideoSize(1920, 1080)
                .setMaxVideoBitrate(START_CAP_BPS));
        return sel;
    }

    /** The playing player keeps 12-30 s of live TV; a standby one just its first few seconds. */
    private ExoPlayer buildPlayer(DefaultTrackSelector sel, boolean standbyPlayer) {
        // Smaller buffers than the default 50 s: live TV doesn't need them and the box has 1 GB of RAM.
        // Play once 1 s is buffered (was 1.5 s): the connection keeps ahead easily, and the first picture comes sooner.
        DefaultLoadControl load = standbyPlayer
                ? new DefaultLoadControl.Builder().setBufferDurationsMs(2_000, 4_000, 1_000, 2_000).build()
                : new DefaultLoadControl.Builder().setBufferDurationsMs(12_000, 30_000, 1_000, 3_000).build();
        return new ExoPlayer.Builder(this).setTrackSelector(sel).setLoadControl(load).setBandwidthMeter(meter).build();
    }

    /** Glow's own listeners (picture, errors, quality log), on whichever player is on screen. */
    private void attachMain(ExoPlayer p) {
        // Quality log: what each channel offers and what's actually being played (GlowPlayer tag).
        if (android.util.Log.isLoggable("GlowZap", android.util.Log.DEBUG)) { // adb shell setprop log.tag.GlowZap DEBUG
            p.addAnalyticsListener(new androidx.media3.exoplayer.analytics.AnalyticsListener() {
                @Override
                public void onLoadCompleted(EventTime t, androidx.media3.exoplayer.source.LoadEventInfo info,
                        androidx.media3.exoplayer.source.MediaLoadData data) {
                    String kind = data.dataType == androidx.media3.common.C.DATA_TYPE_MANIFEST ? "playlist"
                            : data.dataType == androidx.media3.common.C.DATA_TYPE_MEDIA ? "chunk" : "type" + data.dataType;
                    android.util.Log.d("GlowZap", "+" + (android.os.SystemClock.elapsedRealtime() - playStart) + " ms  " + kind
                            + " " + info.bytesLoaded / 1024 + " KB in " + info.loadDurationMs + " ms  " + info.uri.getHost());
                }
            });
        }
        p.addAnalyticsListener(new androidx.media3.exoplayer.analytics.AnalyticsListener() {
            @Override
            public void onVideoInputFormatChanged(EventTime t, androidx.media3.common.Format f,
                    androidx.media3.exoplayer.DecoderReuseEvaluation e) {
                android.util.Log.i("GlowPlayer", current().name + ": playing " + f.width + "x" + f.height
                        + (f.bitrate > 0 ? " " + f.bitrate / 1000 + " kbps" : "") + (f.frameRate > 0 ? " " + Math.round(f.frameRate) + " fps" : "")
                        + " at +" + (android.os.SystemClock.elapsedRealtime() - playStart) + " ms");
            }
        });
        p.addListener(new Player.Listener() {
            @Override
            public void onRenderedFirstFrame() {
                onPicture();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) spinner.setVisibility(View.VISIBLE);
                if (state == Player.STATE_READY) spinner.setVisibility(View.GONE);
            }

            @Override
            public void onPlayerError(PlaybackException e) {
                android.util.Log.i("GlowPlayer", current().name + ": error " + e.getErrorCodeName() + " " + e.getMessage());
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && exo != null) {
                    exo.seekToDefaultPosition(); // fell behind a live stream: jump back to live, same link
                    exo.prepare();
                    return;
                }
                if (playing) {
                    // A stream that was playing dropped: retry from the top of the list rather than giving up.
                    playing = false;
                    tryAttempt(0);
                } else {
                    next();
                }
            }

            @Override
            public void onTracksChanged(androidx.media3.common.Tracks tracks) {
                int best = 0, bestW = 0, n = 0;
                for (androidx.media3.common.Tracks.Group g : tracks.getGroups()) {
                    if (g.getType() != androidx.media3.common.C.TRACK_TYPE_VIDEO) continue;
                    for (int i = 0; i < g.length; i++) {
                        androidx.media3.common.Format f = g.getTrackFormat(i);
                        n++;
                        if (f.height > best) { best = f.height; bestW = f.width; }
                    }
                }
                if (n > 0) android.util.Log.i("GlowPlayer", current().name + ": offers " + n + " video variants, best " + bestW + "x" + best);
                // The stream answered with its renditions, so it's alive: give it 8 s more for the first picture
                // before moving on (Pluto's ad-stitched streams can take ~10 s), once per attempt.
                if (n > 0 && !playing && !extended) {
                    extended = true;
                    root.removeCallbacks(nextAttempt);
                    root.postDelayed(nextAttempt, 8_000);
                }
            }

            @Override
            public void onVideoSizeChanged(VideoSize v) {
                fit(v.width, v.height, v.pixelWidthHeightRatio);
            }
        });
    }

    // ---- standby: the channel above, prepared silently so ▲ switches almost at once ----

    private ExoPlayer standby;
    private DefaultTrackSelector standbyTracks;
    private int standbyIndex = -1, standbyAttempt;
    private long standbyAt;
    private List<Sources.Attempt> standbyList;
    /** A standby younger than this plays from where it is (≤15 s behind live); older ones jump to live first. */
    private static final long STANDBY_FRESH_MS = 15_000;
    private final Runnable prepareStandby = this::prepareStandby;

    /** Loads the next channel's playlists and first seconds in a second, silent player (4 s buffer at most). */
    private void prepareStandby() {
        if (exo == null || Xtream.playlist.size() < 2) return;
        final int ni = (index + 1) % Xtream.playlist.size();
        if (standby != null && standbyIndex == ni) return;
        final Channel ch = Xtream.playlist.get(ni);
        final int gen = generation;
        Net.IO.execute(() -> {
            List<Sources.Attempt> cached = warm.get(ch.key());
            final List<Sources.Attempt> list = cached != null ? cached : Sources.forChannel(this, ch, acct);
            Net.MAIN.post(() -> {
                if (gen != generation || exo == null || isFinishing()) return;
                int k = -1;
                for (int i = 0; i < list.size() && k < 0; i++) {
                    Sources.Attempt a = list.get(i);
                    if (!a.builtIn && a.youtubeChannels == null && a.url != null) k = i;
                }
                if (k < 0) return;
                releaseStandby();
                standbyTracks = newSelector();
                standby = buildPlayer(standbyTracks, true);
                standby.setVolume(0f);
                standby.setPlayWhenReady(false);
                standby.setMediaSource(source(list.get(k)));
                standby.prepare();
                standbyIndex = ni;
                standbyAttempt = k;
                standbyList = list;
                standbyAt = android.os.SystemClock.elapsedRealtime();
                android.util.Log.i("GlowPlayer", "standby: " + ch.name + " prepared in the background");
            });
        });
    }

    private void releaseStandby() {
        if (standby != null) standby.release();
        standby = null;
        standbyIndex = -1;
    }

    /** ▲ onto the prepared channel: swap the standby player onto the screen. False when there's nothing usable. */
    private boolean useStandby(int target) {
        if (standby == null || standbyIndex != target || standby.getPlayerError() != null
                || standby.getPlaybackState() == Player.STATE_IDLE) {
            releaseStandby();
            return false;
        }
        long age = android.os.SystemClock.elapsedRealtime() - standbyAt;
        ExoPlayer old = exo;
        exo = standby;
        tracks = standbyTracks;
        standby = null;
        standbyIndex = -1;
        if (old != null) old.release(); // frees its decoder for the next standby
        attachMain(exo);
        exo.setVideoSurfaceView(surface);
        exo.setVolume(1f);
        if (age > STANDBY_FRESH_MS) exo.seekToDefaultPosition(); // it's been waiting a while: jump to live
        exo.setPlayWhenReady(true);
        attempts = standbyList;
        attempt = standbyAttempt;
        builtIn.setVisibility(View.GONE);
        surface.setVisibility(View.VISIBLE);
        extended = false;
        root.postDelayed(nextAttempt, ATTEMPT_MS);
        android.util.Log.i("GlowPlayer", current().name + ": switched to the prepared player (ready " + age + " ms ago)");
        return true;
    }

    private Channel current() {
        return Xtream.playlist.get(index);
    }

    private void play(int i, boolean zapped) {
        root.removeCallbacks(uncap);
        if (tracks != null) tracks.setParameters(tracks.buildUponParameters().setMaxVideoBitrate(START_CAP_BPS));
        int n = Xtream.playlist.size();
        int target = ((i % n) + n) % n;
        if (target != index) lastIndex = index;
        index = target;
        zapping = zapped;
        pendingYoutube = null;
        final Channel ch = current();
        final int gen = ++generation;
        playing = false;
        playStart = android.os.SystemClock.elapsedRealtime();
        root.removeCallbacks(nextAttempt);
        stopAll();
        curtain.animate().cancel();
        curtain.setAlpha(1f);
        spinner.setVisibility(View.VISIBLE);
        Channel.markWatched(this, ch);
        nowPlaying = ch.name;
        nowChannel = ch;

        int number = GuideLineup.numberOf(ch);
        osdNum.setText(number > 0 ? String.valueOf(number) : "");
        osdName.setText(ch.name);
        showGuide(ch);
        Guide.wantThai(this, ch, () -> { if (current() == ch) showGuide(ch); });
        osdNote.setText("Connecting…");
        osdLogo.setVisibility(ch.logo.isEmpty() ? View.GONE : View.VISIBLE);
        Net.image(osdLogo, ch.logo, Ui.dp(64), () -> osdLogo.setVisibility(View.GONE));
        showOsd(60_000);

        final List<Sources.Attempt> prepared = warm.remove(ch.key());
        Net.IO.execute(() -> {
            final List<Sources.Attempt> list = prepared != null ? prepared : Sources.forChannel(this, ch, acct);
            Net.MAIN.post(() -> {
                if (gen != generation) return; // changed channel meanwhile
                probes.clear();
                if (zapped && standbyIndex == index && useStandby(index)) return;
                attempts = list;
                Sources.probeAll(list, (k, ok) -> Net.MAIN.post(() -> {
                    if (gen != generation) return;
                    probes.put(k, ok);
                    // The link being tried is dead: don't wait for the timer.
                    if (!ok && k == attempt && !playing) next();
                }));
                tryAttempt(0);
            });
        });
    }

    private void stopAll() {
        if (exo != null) exo.stop();
        if (builtIn.getVisibility() == View.VISIBLE) {
            builtIn.stopPlayback();
            builtIn.setVisibility(View.GONE);
        }
    }

    private void next() {
        root.removeCallbacks(nextAttempt);
        tryAttempt(attempt + 1);
    }

    private void tryAttempt(int k) {
        root.removeCallbacks(nextAttempt);
        if (exo == null) return;
        if (k >= attempts.size()) {
            showFailure();
            return;
        }
        // Skip links already known to be dead (the built-in player stays as the last resort).
        while (k < attempts.size() - 1 && Boolean.FALSE.equals(probes.get(k)) && !attempts.get(k).builtIn
                && attempts.get(k).youtubeChannels == null) k++;
        attempt = k;
        Sources.Attempt a = attempts.get(k);
        android.util.Log.i("GlowPlayer", current().name + ": trying " + (k + 1) + "/" + attempts.size()
                + (a.builtIn ? " [built-in] " : " [exo] ") + a.url);
        stopAll();
        spinner.setVisibility(View.VISIBLE);
        if (k > 0) osdNote.setText("That link isn't answering. Trying another source (" + (k + 1) + " of " + attempts.size() + ")…");

        if (a.youtubeChannels != null) {
            findOfficialLive(a);
            return;
        }
        if (a.builtIn) {
            surface.setVisibility(View.GONE);
            builtIn.setVisibility(View.VISIBLE);
            builtIn.setVideoURI(Uri.parse(a.url), a.headers);
            builtIn.start();
        } else {
            builtIn.setVisibility(View.GONE);
            surface.setVisibility(View.VISIBLE);
            exo.setMediaSource(source(a));
            exo.prepare();
        }
        extended = false;
        root.postDelayed(nextAttempt, ATTEMPT_MS);
    }

    static MediaSource source(Sources.Attempt a) {
        Map<String, String> extra = new HashMap<>();
        String ua = DEFAULT_UA;
        if (a.headers != null) {
            for (Map.Entry<String, String> h : a.headers.entrySet()) {
                if (h.getKey().equalsIgnoreCase("User-Agent")) ua = h.getValue();
                else extra.put(h.getKey(), h.getValue());
            }
        }
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(ua)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(8_000)
                .setReadTimeoutMs(10_000)
                .setDefaultRequestProperties(extra);
        MediaItem item = MediaItem.fromUri(a.url);
        String path = Uri.parse(a.url).getPath();
        String p = path == null ? "" : path.toLowerCase();
        boolean progressive = p.endsWith(".ts") || p.endsWith(".mp4") || p.endsWith(".aac") || p.endsWith(".mp3") || p.endsWith(".mkv");
        return progressive ? new ProgressiveMediaSource.Factory(http).createMediaSource(item)
                : new HlsMediaSource.Factory(http).createMediaSource(item);
    }

    /** Direct links failed: look up the broadcaster's YouTube live stream now, and hand over if it's on air. */
    private void findOfficialLive(Sources.Attempt a) {
        root.removeCallbacks(nextAttempt);
        stopAll();
        osdNote.setText("Looking for the official live stream…");
        showOsd(20_000);
        final int gen = generation;
        Net.IO.execute(() -> {
            final String video = OfficialLive.resolve(this, a.youtubeChannels);
            Net.MAIN.post(() -> {
                if (gen != generation || isFinishing()) return;
                if (video == null) {
                    android.util.Log.i("GlowPlayer", current().name + ": not live on YouTube right now");
                    next();
                } else if (zapping) {
                    // Flicking through channels: offer it, don't yank the viewer out of Glow.
                    pendingYoutube = video;
                    spinner.setVisibility(View.GONE);
                    android.util.Log.i("GlowPlayer", current().name + ": live on YouTube, offered (zapping)");
                    osdNote.setText("Live on the broadcaster's YouTube  ·  press OK to watch it there   ·   ▲ ▼ keep zapping");
                    osd.removeCallbacks(hideOsd);
                    osd.animate().alpha(1f).setDuration(150).start();
                } else {
                    handToYouTube(video);
                }
            });
        });
    }

    /** Opens the broadcaster's official YouTube live stream in the YouTube app. */
    private void handToYouTube(String videoId) {
        String url = "https://www.youtube.com/watch?v=" + videoId;
        android.util.Log.i("GlowPlayer", current().name + ": handing over to official YouTube live " + videoId
                + " after " + (android.os.SystemClock.elapsedRealtime() - playStart) + " ms");
        osdNote.setText("Opening the official live stream in YouTube…");
        showOsd(3000);
        android.content.Intent yt = new android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                .setPackage("com.google.android.youtube.tv");
        try {
            startActivity(yt);
        } catch (android.content.ActivityNotFoundException e) {
            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)));
        }
        Channel.markOffline(this, current(), false);
        finish(); // BACK from YouTube returns to the channel list, not into a retry loop
    }

    /** A picture is on screen: remember which link did it. */
    private void onPicture() {
        root.removeCallbacks(prepareStandby);
        root.postDelayed(prepareStandby, 1_000); // get the channel above ready while this one plays
        root.removeCallbacks(uncap);
        root.postDelayed(uncap, 1_500); // picture's up: let ABR climb to full quality
        root.postDelayed(this::saveBandwidth, 8_000);
        if (playing) return;
        playing = true;
        root.removeCallbacks(nextAttempt);
        Channel ch = current();
        Sources.Attempt a = attempts.isEmpty() ? null : attempts.get(attempt);
        android.util.Log.i("GlowPlayer", ch.name + ": PLAYING via source " + (attempt + 1) + "/" + attempts.size()
                + " after " + (android.os.SystemClock.elapsedRealtime() - playStart) + " ms");
        Channel.markOffline(this, ch, false);
        if (a != null) {
            Health.markWorking(this, a.url);
            Sources.rememberWorking(this, ch, a.url);
        }
        spinner.setVisibility(View.GONE);
        curtain.animate().alpha(0f).setDuration(250).start();
        prepareNeighbours();
        osdNote.setText(attempt > 0 ? "Found a working source  ·  ▲ ▼ change channel   ·   OK info"
                : "▲ ▼ change channel   ·   OK info   ·   BACK exit");
        showOsd(4000);
    }

    /** Builds the source lists for the channels above and below and warms their first link's server. */
    private void prepareNeighbours() {
        final int n = Xtream.playlist.size();
        if (n < 2) return;
        final List<Channel> around = new ArrayList<>();
        around.add(Xtream.playlist.get((index + 1) % n));
        around.add(Xtream.playlist.get((index - 1 + n) % n));
        if (lastIndex >= 0 && lastIndex < n) around.add(Xtream.playlist.get(lastIndex));
        warm.keySet().retainAll(java.util.Arrays.asList(around.get(0).key(), around.get(1).key()));
        for (final Channel ch : around) {
            if (warm.containsKey(ch.key())) continue;
            Net.IO.execute(() -> {
                List<Sources.Attempt> list = Sources.forChannel(this, ch, acct);
                warm.put(ch.key(), list);
                if (!list.isEmpty()) Sources.warm(list.get(0));
            });
        }
    }

    /** Digits typed on the remote: jump to that channel number after a short pause. */
    private void goToTypedNumber() {
        if (digits.length() == 0) return;
        int wanted = Integer.parseInt(digits.toString());
        digits.setLength(0);
        List<Channel> list = Xtream.playlist;
        for (int i = 0; i < list.size(); i++) {
            if (GuideLineup.numberOf(list.get(i)) == wanted) {
                play(i, true);
                return;
            }
        }
        // Not in this list: switch to the full numbered lineup, like a satellite box would.
        GuideLineup l = GuideLineup.current();
        if (l != null) {
            List<Channel> all = l.allChannels();
            for (int i = 0; i < all.size(); i++) {
                if (l.number(all.get(i)) == wanted) {
                    Channel was = current();
                    Xtream.playlist = new java.util.ArrayList<>(all);
                    index = Xtream.playlist.indexOf(was); // so "Last" still returns to it (-1 if it isn't numbered)
                    play(i, true);
                    return;
                }
            }
        }
        osdNote.setText("No channel " + wanted + ".");
        showOsd(3000);
    }

    /** Every link failed. Stops and leaves the message up until the viewer presses something. */
    private void showFailure() {
        root.removeCallbacks(nextAttempt);
        stopAll();
        Channel.markOffline(this, current(), true);
        spinner.setVisibility(View.GONE);
        int tried = attempts.size();
        android.util.Log.i("GlowPlayer", current().name + ": FAILED after " + tried + " sources");
        osdNote.setText("Not playing right now" + (tried > 1 ? " — tried " + tried + " sources" : "") + ". Try another with ▲ ▼.");
        osd.removeCallbacks(hideOsd);
        osd.animate().alpha(1f).setDuration(150).start();
    }

    /** Letterbox the video inside the screen at its real shape. */
    private void fit(int w, int h, float pixelRatio) {
        if (w <= 0 || h <= 0 || root.getWidth() == 0) return;
        float video = w * pixelRatio / h, screen = (float) root.getWidth() / root.getHeight();
        int vw = root.getWidth(), vh = root.getHeight();
        if (video > screen) vh = Math.round(vw / video);
        else vw = Math.round(vh * video);
        surface.setLayoutParams(new FrameLayout.LayoutParams(vw, vh, Gravity.CENTER));
    }

    /** "Now: Title · until 21:30   Next 21:30: Title", or nothing when there's no guide for the channel. */
    private void showGuide(Channel ch) {
        Guide.Prog now = Guide.now(ch), next = Guide.next(ch);
        StringBuilder b = new StringBuilder();
        if (now != null) b.append("Now: ").append(now.title).append("  ·  until ").append(Guide.clock(now.stop));
        if (next != null) b.append(b.length() > 0 ? "      " : "").append("Next ").append(Guide.clock(next.start)).append(": ").append(next.title);
        osdNow.setText(b);
        osdNow.setVisibility(b.length() == 0 ? View.GONE : View.VISIBLE);
    }

    private void showOsd(long ms) {
        osd.removeCallbacks(hideOsd);
        osd.animate().alpha(1f).setDuration(150).start();
        osd.postDelayed(hideOsd, ms);
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                play(index + 1, true);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                play(index - 1, true);
                return true;
            case KeyEvent.KEYCODE_LAST_CHANNEL:
                if (lastIndex >= 0 && lastIndex < Xtream.playlist.size()) play(lastIndex, true);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (pendingYoutube != null) {
                    handToYouTube(pendingYoutube);
                    return true;
                }
                showOsd(5000);
                return true;
            case KeyEvent.KEYCODE_GUIDE:
            case KeyEvent.KEYCODE_MENU:
                startActivity(new android.content.Intent(this, ClassicGuideActivity.class).putExtra(ClassicGuideActivity.EXTRA_CHANNEL_KEY, current().key()));
                return true;
            case KeyEvent.KEYCODE_INFO:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                showOsd(5000);
                return true;
            default:
                if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9 && digits.length() < 4) {
                    digits.append((char) ('0' + code - KeyEvent.KEYCODE_0));
                    osdNote.setText("Go to channel " + digits + "_");
                    showOsd(3000);
                    root.removeCallbacks(applyDigits);
                    root.postDelayed(applyDigits, 2000);
                    return true;
                }
                return super.onKeyDown(code, e);
        }
    }
}
