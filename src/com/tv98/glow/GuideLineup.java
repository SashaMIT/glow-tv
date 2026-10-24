package com.tv98.glow;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The TV guide's lineup, classic-satellite style. Every free channel gets a number in its genre's block
 * (Entertainment 101…, Lifestyle 201…, Movies 301…, Sports 401…, News 501…, Documentaries 601…, Kids 701…,
 * Music 801…, Religious/Shopping/Other 901…; past 99 a block carries on at 1100…, 2100… and so on), best-ranked
 * first. Offline channels keep their number (so numbers don't move when a link drops) but aren't listed.
 */
final class GuideLineup {
    static final class Section {
        final String name;
        final List<Channel> channels = new ArrayList<>();

        Section(String name) {
            this.name = name;
        }
    }

    /** Menu 1-9: All Channels, Favourites, then the seven main genres. */
    final List<Section> main = new ArrayList<>();
    /** "0 More…": Music, Religious, Shopping, Recently watched, Thailand, UK, US, My IPTV, Other. */
    final List<Section> more = new ArrayList<>();
    final Map<String, Integer> numbers = new HashMap<>();
    private final List<Channel> everything = new ArrayList<>();

    private static final int[] MAIN_GENRES = {Genres.ENT, Genres.LIFE, Genres.MOVIES, Genres.SPORTS, Genres.NEWS, Genres.DOCS, Genres.KIDS};
    private static final int[] NUMBER_ORDER = {Genres.ENT, Genres.LIFE, Genres.MOVIES, Genres.SPORTS, Genres.NEWS, Genres.DOCS,
            Genres.KIDS, Genres.MUSIC, Genres.RELIGIOUS, Genres.SHOP, Genres.OTHER};

    private static GuideLineup last;
    private static List<Channel> lastFree;
    private static long lastAt;

    /**
     * Call off the main thread. The numbering is kept for 30 minutes while the channel lists are unchanged,
     * so reopening the guide is instant; favourites and recently watched are always re-read.
     */
    static synchronized GuideLineup build(Context c) {
        List<Channel> free = FreeTv.cached(c);
        if (last != null && free == lastFree && System.currentTimeMillis() - lastAt < 30 * 60 * 1000L) {
            last.refreshPersonal(c);
            return last;
        }
        Guide.loadSaved(c);
        long t0 = android.os.SystemClock.elapsedRealtime();
        GuideLineup g = new GuideLineup();

        // One entry per link (the same channel can be in the Thai, UK, US, news and sports lists).
        Map<String, Channel> byKey = new java.util.LinkedHashMap<>();
        for (Channel ch : free) if (!byKey.containsKey(ch.key())) byKey.put(ch.key(), ch);
        List<Channel> all = new ArrayList<>(byKey.values());
        final Map<Channel, Integer> order = new HashMap<>();
        for (int i = 0; i < all.size(); i++) order.put(all.get(i), i);

        // Genres and the ranked members of each genre.
        Map<Channel, List<Integer>> genresOf = new HashMap<>();
        List<List<Channel>> members = new ArrayList<>();
        for (int i = 0; i < Genres.NAMES.length; i++) members.add(new ArrayList<>());
        for (Channel ch : all) {
            List<Integer> gs = Genres.of(ch);
            if (gs.isEmpty()) continue; // adult: never listed
            genresOf.put(ch, gs);
            for (int genre : gs) members.get(genre).add(ch);
        }
        long t1 = android.os.SystemClock.elapsedRealtime();
        final Map<Channel, Integer> base = new HashMap<>();
        for (Channel ch : genresOf.keySet()) base.put(ch, Genres.base(ch));
        for (int genre = 0; genre < Genres.NAMES.length; genre++) members.set(genre, rank(genre, members.get(genre), order, base));
        long t2 = android.os.SystemClock.elapsedRealtime();

        // Numbers: each channel in its primary genre's block, in ranked order. A channel keeps the number it
        // had last time (saved in numbers.tsv) as long as it's still in the same genre; new channels take the
        // next free number in their block.
        Map<String, Integer> saved = loadSaved(c);
        Set<Integer> taken = new HashSet<>();
        for (int genre : NUMBER_ORDER) {
            for (Channel ch : members.get(genre)) {
                Integer old = saved.get(ch.key());
                if (genresOf.get(ch).get(0) != genre || old == null || blockOf(old) != Genres.BLOCK[genre]) continue;
                if (taken.add(old)) g.numbers.put(ch.key(), old);
            }
        }
        int[] used = new int[11];
        for (int genre : NUMBER_ORDER) {
            int block = Genres.BLOCK[genre];
            for (Channel ch : members.get(genre)) {
                if (genresOf.get(ch).get(0) != genre || g.numbers.containsKey(ch.key())) continue;
                int n;
                do {
                    int k = ++used[block];
                    n = k <= 99 ? block * 100 + k : k <= 999 ? block * 1000 + k : 10_000 + block * 1000 + k;
                } while (!taken.add(n));
                g.numbers.put(ch.key(), n);
            }
        }
        Map<String, Integer> toSave = new HashMap<>(g.numbers);
        if (!toSave.equals(saved)) save(c, toSave);
        Xtream.Account acct = Xtream.load(c);
        List<Channel> mine = acct == null ? new ArrayList<Channel>() : Xtream.cached(c);
        for (int i = 0; i < mine.size(); i++) g.numbers.put(mine.get(i).key(), 20_001 + i);

        // Sections: only channels that currently work.
        Section everythingOn = new Section("All Channels");
        for (Channel ch : all) {
            if (g.numbers.containsKey(ch.key()) && !Channel.isOffline(c, ch)) everythingOn.channels.add(ch);
        }
        everythingOn.channels.addAll(mine);
        long t3 = android.os.SystemClock.elapsedRealtime();
        android.util.Log.i("GlowPerf", "lineup: genres " + (t1 - t0) + " ms, ranking " + (t2 - t1) + " ms, offline checks " + (t3 - t2) + " ms");
        g.sortByNumber(everythingOn.channels);
        g.everything.addAll(everythingOn.channels);
        Set<String> on = new HashSet<>();
        for (Channel ch : everythingOn.channels) on.add(ch.key());

        g.main.add(everythingOn);
        g.main.add(new Section("Favourites"));
        for (int genre : MAIN_GENRES) g.main.add(g.genreSection(genre, members.get(genre), on));

        g.more.add(g.genreSection(Genres.MUSIC, members.get(Genres.MUSIC), on));
        g.more.add(g.genreSection(Genres.RELIGIOUS, members.get(Genres.RELIGIOUS), on));
        g.more.add(g.genreSection(Genres.SHOP, members.get(Genres.SHOP), on));
        g.more.add(new Section("Recently watched"));
        String[][] lists = {{"countries/th.m3u", "Thailand"}, {"countries/uk.m3u", "UK"}, {"countries/us.m3u", "US"}};
        for (String[] l : lists) {
            Section s = new Section(l[1]);
            Set<String> seen = new HashSet<>();
            for (Channel ch : Xtream.inCategory(free, l[0])) {
                Channel one = byKey.get(ch.key());
                if (one != null && on.contains(one.key()) && seen.add(one.key())) s.channels.add(one);
            }
            g.sortByNumber(s.channels);
            g.more.add(s);
        }
        if (acct != null) {
            Section s = new Section("My IPTV");
            s.channels.addAll(mine);
            g.more.add(s);
        }
        g.more.add(g.genreSection(Genres.OTHER, members.get(Genres.OTHER), on));

        if (android.util.Log.isLoggable("GlowRank", android.util.Log.DEBUG)) { // adb shell setprop log.tag.GlowRank DEBUG
            for (Section s : g.main) {
                StringBuilder b = new StringBuilder(s.name).append(":");
                for (int i = 0; i < Math.min(15, s.channels.size()); i++) {
                    Channel ch = s.channels.get(i);
                    b.append(" | ").append(g.number(ch)).append(' ').append(ch.name);
                }
                android.util.Log.d("GlowRank", b.toString());
            }
        }
        g.refreshPersonal(c);
        last = g;
        lastFree = free;
        lastAt = System.currentTimeMillis();
        return g;
    }

    /**
     * Ranked order inside a genre: {@link Genres#score}, then regional copies ("Red Bull TV DE" after
     * "Red Bull TV", "ABC News Live 4" after "ABC News Live") moved below the first of their name.
     */
    private static List<Channel> rank(final int genre, List<Channel> list, final Map<Channel, Integer> order,
            Map<Channel, Integer> base) {
        final Map<Channel, Integer> score = new HashMap<>();
        for (Channel ch : list) score.put(ch, Genres.score(genre, ch, base.get(ch)));
        List<Channel> out = new ArrayList<>(list);
        Collections.sort(out, (a, b) -> {
            int d = Integer.compare(score.get(a), score.get(b));
            return d != 0 ? d : Integer.compare(order.get(a), order.get(b));
        });
        Set<String> seen = new HashSet<>();
        boolean moved = false;
        for (Channel ch : out) {
            String n = Genres.lower(ch.name);
            for (int sp = n.indexOf(' '); sp > 0; sp = n.indexOf(' ', sp + 1)) {
                if (seen.contains(n.substring(0, sp))) {
                    score.put(ch, score.get(ch) + 250);
                    moved = true;
                    break;
                }
            }
            seen.add(n);
        }
        if (moved) {
            Collections.sort(out, (a, b) -> {
                int d = Integer.compare(score.get(a), score.get(b));
                return d != 0 ? d : Integer.compare(order.get(a), order.get(b));
            });
        }
        return out;
    }

    private Section genreSection(int genre, List<Channel> ranked, Set<String> on) {
        Section s = new Section(Genres.NAMES[genre]);
        for (Channel ch : ranked) if (on.contains(ch.key())) s.channels.add(ch);
        return s;
    }

    private void sortByNumber(List<Channel> list) {
        Collections.sort(list, (a, b) -> Integer.compare(number(a), number(b)));
    }

    private void refreshPersonal(Context c) {
        Section favs = main.get(1);
        favs.channels.clear();
        favs.channels.addAll(Picks.favourites(c, everything));
        for (Section s : more) {
            if (s.name.equals("Recently watched")) {
                s.channels.clear();
                s.channels.addAll(Channel.recent(c, everything));
            }
        }
    }

    // ---- saved numbers ----

    /** Bump when the genre top lists change enough that everything should be renumbered once. */
    private static final String VERSION = "glow-numbers 1";

    private static java.io.File numbersFile(Context c) {
        return new java.io.File(c.getFilesDir(), "numbers.tsv");
    }

    private static Map<String, Integer> loadSaved(Context c) {
        Map<String, Integer> out = new HashMap<>();
        java.io.File f = numbersFile(c);
        if (!f.exists()) return out;
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(
                new java.io.FileInputStream(f), java.nio.charset.StandardCharsets.UTF_8))) {
            if (!VERSION.equals(r.readLine())) return out;
            for (String line; (line = r.readLine()) != null; ) {
                int tab = line.lastIndexOf('\t');
                if (tab > 0) out.put(line.substring(0, tab), Integer.parseInt(line.substring(tab + 1)));
            }
        } catch (Exception e) {
            out.clear();
        }
        return out;
    }

    private static void save(Context c, Map<String, Integer> numbers) {
        java.io.File tmp = new java.io.File(c.getFilesDir(), "numbers.tsv.tmp");
        try (java.io.Writer w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(tmp), java.nio.charset.StandardCharsets.UTF_8)) {
            StringBuilder b = new StringBuilder(VERSION).append('\n');
            for (Map.Entry<String, Integer> e : numbers.entrySet()) {
                if (e.getValue() < 20_000) b.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
            }
            w.write(b.toString());
        } catch (Exception e) {
            tmp.delete();
            return;
        }
        tmp.renameTo(numbersFile(c));
    }

    /** 401 -> 4, 4123 -> 4, 14123 -> 4. */
    private static int blockOf(int n) {
        if (n >= 10_000) n -= 10_000;
        return n < 1_000 ? n / 100 : n / 1_000;
    }

    /** The guide number for a channel anywhere in Glow (0 before the lineup has been built). */
    static int numberOf(Channel ch) {
        GuideLineup l = last;
        if (l == null) return ch.num;
        int n = l.number(ch);
        return n > 0 ? n : ch.num;
    }

    /** The current lineup if it's been built (Home builds it in the background at start), else null. */
    static GuideLineup current() {
        return last;
    }

    /** All working channels in number order. */
    List<Channel> allChannels() {
        return main.get(0).channels;
    }

    int number(Channel ch) {
        Integer n = numbers.get(ch.key());
        return n == null ? 0 : n;
    }
}
