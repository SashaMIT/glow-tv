package com.tv98.glow;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Glow's rules, checked against what they should give. No framework: {@code tools/test.sh} runs this. */
public final class GlowTests {
    private static int passed;
    private static final List<String> failed = new ArrayList<>();

    private static void same(String what, Object got, Object want) {
        if (want == null ? got == null : want.equals(got)) passed++;
        else failed.add(what + "\n    got  " + got + "\n    want " + want);
    }

    private static Channel channel(String name, String group, String country) {
        Channel c = new Channel(1, 1, name, null, "news", "https://example.com/a.m3u8", null, null, group);
        c.country = country;
        return c;
    }

    private static void listings() throws Exception {
        same("entities", XmltvScan.unescape("Tom &amp; Jerry &#39;1&#x27; &lt;b&gt; &nope; a&b"), "Tom & Jerry '1' <b> &nope; a&b");
        same("short text stays", XmltvScan.shorten(" Hello\nworld ", 50), "Hello world");
        same("cut at a word", XmltvScan.shorten("one two three four five six", 15), "one two three…");
        same("nothing to shorten", XmltvScan.shorten("", 10), null);
        long utc = 1793232000000L; // 2026-10-29 00:00 UTC
        same("time in UTC", XmltvScan.time("20261029000000 +0000"), utc);
        same("time east of UTC", XmltvScan.time("20261029070000 +0700"), utc);
        same("time west of UTC", XmltvScan.time("20261028190000 -0500"), utc);
        same("time with no zone", XmltvScan.time("20261029000000"), utc);
        same("bad time", XmltvScan.time("2026"), -1L);

        String xml = "<tv><channel id=\"a.uk\"><display-name>A</display-name></channel>"
                + "<programme start=\"20261029000000 +0000\" stop=\"20261029010000 +0000\" channel=\"a.uk\"><title>First &amp; best</title><desc>About it</desc></programme>"
                + "<programme start=\"20261029010000 +0000\" stop=\"20261029020000 +0000\" channel=\"a.uk\"><title>Second</title></programme>"
                + "<programme start=\"20261029000000 +0000\" stop=\"20261029010000 +0000\" channel=\"other\"><title>Not ours</title></programme>"
                + "<programme start=\"20261101000000 +0000\" stop=\"20261101010000 +0000\" channel=\"a.uk\"><title>Too late</title></programme></tv>";
        Map<String, List<String>> ids = new HashMap<>();
        ids.put("a.uk", Collections.singletonList("key-a"));
        Map<String, List<Guide.Prog>> out = new HashMap<>();
        XmltvScan.scan(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), ids, null, utc, utc + 86_400_000L, out, 100);
        List<Guide.Prog> a = out.get("key-a");
        same("programmes in the window", a == null ? -1 : a.size(), 2);
        if (a != null && a.size() == 2) {
            same("programme title", a.get(0).title, "First & best");
            same("programme start", a.get(0).start, utc);
            same("programme stop", a.get(1).stop, utc + 2 * 3_600_000L);
        }
        same("other channels left out", out.size(), 1);
    }

    private static void genres() {
        same("news", Genres.of(channel("X", "News", "uk")), Collections.singletonList(Genres.NEWS));
        same("adult is never shown", Genres.of(channel("X", "XXX", null)), new ArrayList<Integer>());
        same("sports comes before news", Genres.of(channel("X", "Sports;News", null)), Arrays.asList(Genres.SPORTS, Genres.NEWS));
        same("no category: the list it came from", Genres.of(channel("X", null, null)), Collections.singletonList(Genres.NEWS));
        same("pay re-stream isn't promoted", Genres.neverPromote(channel("Star Sports 1", "Sports", "in")), true);
        same("SuperSport isn't promoted", Genres.neverPromote(channel("SuperSport Action", "Sports", null)), true);
        same("free channel can be promoted", Genres.neverPromote(channel("Red Bull TV", "Sports", null)), false);
        same("Tennis Channel isn't promoted", Genres.neverPromote(channel("Tennis Channel", "Sports", "us")), true);
        same("English country", Genres.language(channel("Some Channel", "News", "uk")), 0);
        same("Thai country", Genres.language(channel("Some Channel", "News", "th")), 0);
        same("unknown language", Genres.language(channel("Some Channel", "News", null)), 1);
        same("other country", Genres.language(channel("Some Channel", "News", "fr")), 2);
    }

    private static void tonight() {
        same("highlights are a replay", Tonight.replay("premier league highlights"), true);
        same("a past year is a replay", Tonight.replay("world cup 2019 final"), true);
        same("a fight night isn't", Tonight.replay("arsenal v spurs live"), false);
        same("filler", Tonight.filler("paid programming"), true);
        same("filler with more words", Tonight.filler("shopping hour"), true);
        same("close alone is filler", Tonight.filler("close"), true);
        same("Close Encounters is a film", Tonight.filler("close encounters of the third kind"), false);
        same("a real title", Tonight.filler("the numbers station"), false);

        List<Integer> sport = Collections.singletonList(Genres.SPORTS), film = Collections.singletonList(Genres.MOVIES),
                news = Collections.singletonList(Genres.NEWS), docs = Collections.singletonList(Genres.DOCS),
                ent = Collections.singletonList(Genres.ENT);
        same("live sport", kind("Arsenal v Spurs LIVE", sport, 120), "Live sport");
        same("a match", kind("Arsenal v Spurs", sport, 120), "Sport");
        same("sport magazine left out", kind("Tennis Today", sport, 60), null);
        same("a replay left out", kind("Arsenal v Spurs highlights", sport, 60), null);
        same("film length", kind("Face/Off", film, 138), "Film");
        same("too short for a film", kind("Trailers", film, 30), null);
        same("too long for a film", kind("All-day movie loop", film, 400), null);
        same("premiere", kind("New: The Show", ent, 60), "Premiere");
        same("news special", kind("Election night", news, 120), "News special");
        same("ordinary news left out", kind("The Hour", news, 60), null);
        same("documentary", kind("Planet", docs, 50), "Documentary");
        same("short documentary left out", kind("Planet", docs, 30), null);
        same("live sport beats a film", Tonight.classify("A v B live", "a v b live", sport, 120).score > Tonight.classify("Film", "film", film, 120).score, true);
    }

    private static String kind(String title, List<Integer> genres, long minutes) {
        Tonight.Pick k = Tonight.classify(title, title.toLowerCase(java.util.Locale.ROOT).trim(), genres, minutes);
        return k == null ? null : k.kind;
    }

    private static void radio() {
        same("title by artist", Radio.query("Jungle River Boat by Les Baxter - Classic Vinyl on walmradio.com", "Classic Vinyl HD"), "Les Baxter Jungle River Boat");
        same("artist - title", Radio.query("Queen - Bohemian Rhapsody (Remastered)", "Rock FM"), "Queen Bohemian Rhapsody");
        same("station's own name left out", Radio.query("Rock FM - Queen", "Rock FM"), "Queen");
        same("nothing sent", Radio.query(null, "x"), "");
        same("right artist", Radio.namesArtist("Roger Whittaker It's Your Love", "Roger Whittaker"), true);
        same("wrong artist", Radio.namesArtist("Roger Whittaker It's Your Love", "Destiny's Child"), false);
        same("\"the\" alone doesn't match", Radio.namesArtist("The Greatest Love", "The Who"), false);
        same("part of the name", Radio.namesArtist("Nelson Riddle My One And Only Love", "Nelson Riddle & His Orchestra"), true);
    }

    private static Markets.Quote quote(String symbol, double price, double change, boolean currency) {
        Markets.Quote q = new Markets.Quote();
        q.symbol = symbol;
        q.price = price;
        q.change = change;
        q.currency = currency;
        return q;
    }

    private static void markets() {
        same("big price", Markets.price(quote("BTC", 79939.4, 0, false)), "$79,939");
        same("small price", Markets.price(quote("ETH", 123.456, 0, false)), "$123.46");
        same("baht", Markets.price(quote("USD/THB", 33.754, 0, true)), "฿33.75");
        same("dollar rate", Markets.price(quote("GBP/USD", 1.33681, 0, true)), "$1.3368");
        same("up", Markets.change(quote("X", 1, 1.26, false)), "+1.3%");
        same("down uses a real minus", Markets.change(quote("X", 1, -0.72, false)), "−0.7%");
        List<Markets.Quote> qs = Arrays.asList(quote("BTC", 79939, -0.7, false), quote("XAU", 4013, 0.4, false), quote("USD/THB", 33.75, 0, true));
        same("Home's line", Markets.line(qs), "BTC $79,939 −0.7%   ·   Gold $4,013 +0.4%");
        same("the dollar in baht", Markets.baht(qs), "$1 = ฿33.75");
        same("no baht", Markets.baht(Collections.singletonList(quote("BTC", 1, 0, false))), "");
    }

    private static void f1() throws Exception {
        same("lap time", F1Timing.clock(94.035, false), "1:34.035");
        same("race time", F1Timing.clock(5430.5, true), "1:30:30.500");
        same("gap", F1Timing.gap(new JSONObject("{\"gap_to_leader\":13.078}"), true, false), "+13.078");
        same("lapped", F1Timing.gap(new JSONObject("{\"gap_to_leader\":\"+1 LAP\"}"), true, false), "+1 LAP");
        same("retired", F1Timing.gap(new JSONObject("{\"dnf\":true}"), true, false), "DNF");
        same("qualifying: last session reached", F1Timing.gap(new JSONObject("{\"gap_to_leader\":[0.2,0.3,null]}"), false, false), "+0.300");
        same("leader shows the time", F1Timing.gap(new JSONObject("{\"duration\":94.035}"), false, true), "1:34.035");
        same("OpenF1 time", F1Timing.iso("2026-10-29T00:00:00+00:00"), 1793232000000L);
        same("bad time sorts last", F1Timing.iso("nope"), Long.MAX_VALUE);
        same("soft tyre", F1Timing.tyreColour("SOFT"), 0xFFE8002D);
        same("unknown tyre", F1Timing.tyreColour("???"), 0xFF777777);
    }

    private static void sports() {
        same("ESPN time", Sports.time("2026-10-29T00:00Z"), 1793232000000L);
        same("bad ESPN time", Sports.time("x"), 0L);
        same("a match looks live", IntelActivity.looksLive("Arsenal v Spurs"), true);
        same("live in the title", IntelActivity.looksLive("Sunday Live: Golf"), true);
        same("highlights don't", IntelActivity.looksLive("Arsenal v Spurs highlights"), false);
        same("a magazine doesn't", IntelActivity.looksLive("Tennis Today"), false);
    }

    private static void cams() {
        byte[] page = "x\"isLive\":true,y".getBytes(StandardCharsets.US_ASCII);
        same("live marker found", Cams.contains(page, "\"isLive\":true"), true);
        same("live marker missing", Cams.contains(page, "\"isLive\":false"), false);
        same("empty page", Cams.contains(new byte[0], "a"), false);
    }

    private static void vitals() {
        List<String> log = Arrays.asList("1000 500 60", "1300 800 81", "1600 100 55", "garbage", "1900 400 70");
        Vitals.Summary v = Vitals.summarize(log, 2000);
        same("latest temperature", v.now, 70);
        same("hottest", v.hottest, 81);
        same("temperature before the restart", v.beforeRestart, 81);
        same("restart time", v.restartAt, 1500L);
        same("hottest only counts 24 h", Vitals.summarize(Arrays.asList("1000 500 90", "90000 500 60"), 90000).hottest, 60);
        same("no restart", Vitals.summarize(Arrays.asList("1000 500 60", "1300 800 61"), 2000).restartAt, 0L);
        same("empty log", Vitals.summarize(new ArrayList<String>(), 2000).now, -1);
        same("no crashes", Vitals.countCrashes(""), 0);
        same("two crashes", Vitals.countCrashes("1793232000 1.6.0 com.tv98.glow main\njava.lang.Error: x\n\tat a.b(c)\n\n1793232100 1.6.0 com.tv98.glow:cam main\njava.lang.Error\n"), 2);
    }

    private static void feeds() {
        same("host", Feeds.host("https://site.api.espn.com/a/b?c=d"), "site.api.espn.com");
        same("host with a port", Feeds.host("http://192.168.1.5:8123/api"), "192.168.1.5");
        same("not an address", Feeds.host("nope"), "");
        Feeds.clear();
        Feeds.report("https://a.example/x", null, 0);
        Feeds.report("https://b.example/x", "HTTP 503", 0);
        same("answering", Feeds.answering(), 1);
        same("failing", Feeds.failing(300_000L), Collections.singletonList("b.example: HTTP 503, 5 min ago"));
        Feeds.report("https://b.example/y", null, 400_000L);
        same("recovered", Feeds.failing(400_000L).size(), 0);
        same("both answering", Feeds.answering(), 2);
    }

    private static byte[] hex(String h) {
        byte[] b = new byte[h.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        return b;
    }

    private static void signed() {
        // Signed with the nightly job's own key and openssl, as the job does it.
        byte[] key = java.util.Base64.getDecoder().decode(Signed.key());
        byte[] body = "data".getBytes(StandardCharsets.US_ASCII);
        byte[] sig = hex("304502200bfa41fa9545d706a289a87ca7085287a928850fecc347b5daf4bbf1e84751e5022100f9697ec7666eb62fe15fccbd698126019a874dc4371bc289c4f2340244da58ea");
        same("a true signature passes", Signed.verify(body, sig, key), true);
        same("changed data fails", Signed.verify("Data".getBytes(StandardCharsets.US_ASCII), sig, key), false);
        byte[] bad = sig.clone();
        bad[20] ^= 1;
        same("a changed signature fails", Signed.verify(body, bad, key), false);
        same("nonsense fails", Signed.verify(body, new byte[8], key), false);
    }

    private static void stamps() {
        long m = 60_000L, fresh = 15 * m;
        same("just answered", Intel.stamp("USGS", 20_000L, fresh), "USGS   ·   updated just now");
        same("minutes", Intel.stamp("USGS", 4 * m, fresh), "USGS   ·   updated 4 min ago");
        same("still within twice its refresh", Intel.stamp("USGS", 30 * m, fresh), "USGS   ·   updated 30 min ago");
        same("past it: says it can't reach it", Intel.stamp("USGS", 3 * 60 * m, fresh), "USGS   ·   can't reach it, this is from 3 h ago");
        same("days", Intel.stamp("USGS", 3 * 1440 * m, fresh), "USGS   ·   can't reach it, this is from 3 days ago");
        same("never answered", Intel.stamp("USGS", -1, fresh), "USGS");
    }

    private static String data;

    private static String file(String name) throws Exception {
        return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(data, name)), StandardCharsets.UTF_8);
    }

    /** Real samples of each feed (test/data): if a source changes its format, a new sample shows it here first. */
    private static void feedsParse() throws Exception {
        List<Intel.Event> q = Intel.parseQuakes(file("usgs.json"));
        same("two earthquakes", q.size(), 2);
        same("quake title", q.get(1).title, "M5.5 earthquake");
        same("quake place", q.get(1).detail.startsWith("74 km NE of Hasaki, Japan  ·  depth "), true);
        same("M5.5 is notable", q.get(1).severity, 1);
        same("M4.9 is low", q.get(0).severity, 0);
        same("quake has a position", q.get(1).lat > 30 && q.get(1).lat < 40 && q.get(1).lon > 130, true);
        same("nothing fetched", Intel.parseQuakes(null).size(), 0);
        same("not JSON", Intel.parseQuakes("<html>down</html>").size(), 0);

        List<Intel.Event> n = Intel.parseNatural(file("eonet.json"));
        same("two natural events", n.size(), 2);
        same("wildfire", n.get(0).category, "Wildfires");
        same("storm's name", n.get(1).title, "Tropical Cyclone Murasu");

        List<Intel.Event> a = Intel.parseAlerts(file("gdacs.json"));
        same("orange alerts kept", a.size() >= 1, true);
        same("alert source", a.get(0).source, "GDACS");
        same("orange is notable", a.get(0).severity, 1);
        same("drought", a.get(0).category, "Drought");

        List<Intel.Market> m = Intel.parseMarkets(file("polymarket.json"));
        same("sport bets left out", m.size(), 5);
        same("first question", m.get(0).question.startsWith("Will Abdul El-Sayed"), true);
        same("odds between 0 and 1", m.get(0).yes >= 0 && m.get(0).yes <= 1, true);
    }

    public static void main(String[] args) throws Exception {
        data = args[0];
        feedsParse();
        stamps();
        signed();
        feeds();
        vitals();
        listings();
        genres();
        tonight();
        radio();
        markets();
        f1();
        sports();
        cams();
        for (String f : failed) System.out.println("FAILED " + f);
        System.out.println(passed + " passed, " + failed.size() + " failed");
        if (!failed.isEmpty()) System.exit(1);
    }
}
