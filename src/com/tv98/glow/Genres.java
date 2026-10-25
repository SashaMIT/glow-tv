package com.tv98.glow;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Sky-style genres for the TV guide, and how channels are ranked inside each one.
 *
 * Rank (lower = higher up), in order of weight:
 *   1. hand-picked "top of the genre" list (e.g. fight and action sports first in Sports, the big English-language
 *      news channels first in News);
 *   2. never promoted: pay-TV channels that only appear as unofficial re-streams (ESPN, CNN, Fox News Channel…);
 *   3. language: English or Thai first, unknown next, other languages last;
 *   4. regional copies and local affiliates ("Red Bull TV DE", "CBS News Boston", "ABC News Live 7") after the main one;
 *   5. well-known free streaming brands (Pluto TV, Rakuten TV, PBS…) and channels with programme listings first.
 * Personal history isn't used here so channel numbers stay put; it has its own "Recently watched" list.
 */
final class Genres {
    static final int ENT = 0, LIFE = 1, MOVIES = 2, SPORTS = 3, NEWS = 4, DOCS = 5, KIDS = 6, MUSIC = 7, RELIGIOUS = 8,
            SHOP = 9, OTHER = 10;
    static final String[] NAMES = {"Entertainment", "Lifestyle & Culture", "Movies", "Sports", "News", "Documentaries",
            "Kids", "Music", "Religious", "Shopping", "Other"};
    /** Number blocks, Sky style: Entertainment 101…, Lifestyle 201…, Movies 301…, Sports 401…, News 501… */
    static final int[] BLOCK = {1, 2, 3, 4, 5, 6, 7, 8, 9, 9, 9};
    /** When a channel has several genres, the most specific one gives its number. */
    private static final int[] PRIMARY_ORDER = {SPORTS, NEWS, KIDS, MOVIES, DOCS, LIFE, ENT, MUSIC, RELIGIOUS, SHOP, OTHER};

    private Genres() {}

    /** All genres of a channel (from iptv-org's categories), most specific first. Empty = adult, never shown. */
    static List<Integer> of(Channel ch) {
        boolean[] in = new boolean[NAMES.length];
        String g = ch.group == null ? "" : ch.group.toLowerCase(Locale.ROOT);
        if (g.contains("xxx")) return new ArrayList<>();
        for (String w : g.split(" ")) {
            switch (w) {
                case "general": case "entertainment": case "series": case "comedy": case "classic": case "family":
                case "interactive": case "public":
                    in[ENT] = true; break;
                case "lifestyle": case "culture": case "cooking": case "travel": case "auto": case "relax": case "outdoor":
                    in[LIFE] = true; break;
                case "movies": in[MOVIES] = true; break;
                case "sports": in[SPORTS] = true; break;
                case "news": case "business": case "weather": case "legislative": in[NEWS] = true; break;
                case "documentary": case "science": case "education": in[DOCS] = true; break;
                case "kids": case "animation": in[KIDS] = true; break;
                case "music": in[MUSIC] = true; break;
                case "religious": in[RELIGIOUS] = true; break;
                case "shop": in[SHOP] = true; break;
                default: break;
            }
        }
        List<Integer> out = new ArrayList<>();
        for (int genre : PRIMARY_ORDER) if (in[genre]) out.add(genre);
        if (out.isEmpty()) { // no category: go by the list it came from
            String list = ch.categoryId == null ? "" : ch.categoryId;
            out.add(list.contains("news") ? NEWS : list.contains("sports") ? SPORTS : OTHER);
        }
        return out;
    }

    // ---- hand-picked top of each genre ("=" exact name, otherwise "name" or "name ..." prefix) ----

    private static final String[][] TOP = new String[NAMES.length][];

    static {
        TOP[SPORTS] = new String[] {
                // fight & action sports first
                "=red bull tv", "=red bull tv uk", "=red bull tv us", "=fuel tv", "=dazn combat", "=bellator mma", "=pfl mma",
                "glory kickboxing", "=fightbox hd", "=fightbox", "=swerve combat", "=fast&funbox", "=wwe network",
                "=monster jam", "=tvs boxing", "=introuble", "=strongman uk",
                // then the big free sports channels
                "=bein sports xtra", "=cbs sports hq", "=cbs sports golazo network", "=fifa+", "=nfl channel", "=mlb",
                "=stadium", "=pga tour", "=rugbypass tv", "=tennis channel 2", "=racer international", "=racer select",
                "espn8", "=nbc sports now", "=t sports 7", "=real madrid tv", "=pokergo", "=world poker tour uk",
                "=floracing", "=fifa+ women", "dazn darts", "=pluto tv competition"};
        TOP[NEWS] = new String[] {
                "=bbc news uk hd", "=bbc news", "=sky news", "=al jazeera english hd", "=al jazeera english",
                "=cna international", "=dw english", "=france 24 english", "=nbc news now", "=abc news live",
                "=cbs news 24/7", "=bloomberg tv", "reuters", "=livenow from fox", "=gb news", "=euronews english hd",
                "=euronews english", "=nhk world-japan hd", "=nhk world-japan", "=trt world hd", "=trt world", "=wion",
                "=arirang tv hd", "=arirang tv", "=scripps news", "=newsnation", "=newsmax tv", "=cheddar news",
                "=bbc news north america", "=bbc news south asia", "=sky news extra 1", "=fox weather", "=accuweather now",
                "=thai pbs", "=thairath tv", "=tnn16", "=nation tv", "=top news", "=nbt 2 hd", "=bbc parliament"};
        TOP[ENT] = new String[] {
                "=pluto tv reality", "=pluto tv game shows", "=comedy central pluto tv", "=mtv pluto tv",
                "=wipeout xtra", "=pluto tv comedy", "=pluto tv sitcoms gb", "=nbc comedy vault", "=pluto tv crime gb",
                "=pluto tv investigation", "=pluto tv the twilight zone", "=pluto tv classic tv", "=bet pluto tv",
                "=grit xtra", "=3hd", "=channel 7 (thailand)", "=workpoint tv", "=one 31", "=gmm 25", "=amarin tv",
                "=mono 29", "=channel 8 (thailand)", "=true4u", "=pptv hd 36", "=jkn 18"};
        TOP[MOVIES] = new String[] {
                "=pluto tv movies gb", "rakuten tv action movies", "rakuten tv comedy movies", "rakuten tv", "=pluto tv action",
                "=pluto tv thrillers", "=pluto tv trending now", "=pluto tv staff picks", "=pluto tv spotlight",
                "=pluto tv icons", "=pluto tv franchise favorites", "=pluto tv crime movies", "pluto tv sci-fi",
                "pluto tv horror", "=pluto tv family movie club", "pluto tv cult films", "=00s replay"};
        TOP[DOCS] = new String[] {
                "=bbc earth us", "=pbs nature", "=pluto tv history", "=pluto tv science", "=pluto tv animals gb", "=waterbear",
                "=adventure earth", "=pluto tv britain at war", "pluto tv true crime", "=pluto tv american true crime",
                "pluto tv space", "=bloomberg originals", "=pluto tv petrolheads", "=pluto tv conspiracy"};
        TOP[KIDS] = new String[] {
                "=pbs kids", "=nick jr. pluto tv", "=nickelodeon pluto tv", "=pluto tv retro toons gb", "=pluto tv anime",
                "=pluto tv animation"};
        TOP[LIFE] = new String[] {
                "=red bull tv", "=outside tv", "=bbc food", "=bbc home & garden", "=pbs travel", "=pluto tv food gb",
                "=horse & country tv", "=pluto tv cars", "=pluto tv adventure"};
        TOP[MUSIC] = new String[] {"=mtv pluto tv", "vevo", "=stingray", "=clubbing tv", "=trace"};
    }

    /** Pay-TV channels: in free lists they're unofficial re-streams, so they're never pushed up. */
    private static final String[] NEVER_PROMOTE = {"espn", "espnu", "cnn", "fox news channel", "fox business", "fox sports",
            "nba tv", "nfl network", "nhl network", "golf channel", "eurosport", "sky sports", "sky racing", "bein sports",
            "willow", "ten cricket", "mutv", "bbc america", "cctv-", "tnt sports", "hbo", "showtime", "starz", "cinemax",
            "amc", "discovery", "national geographic", "nat geo", "disney", "cartoon network", "nickelodeon", "nick jr"};
    /** Free channels whose names start like a pay one. */
    private static final String[] FREE_EXCEPTIONS = {"espn8", "cnn originals", "bein sports xtra", "nickelodeon pluto tv",
            "nick jr. pluto tv", "tennis channel 2", "tennis channel +2"};

    /** Words that mark a non-English feed (whole words), and country codes at the end of a name ("… DK"). */
    private static final Set<String> OTHER_WORDS = new HashSet<>(Arrays.asList(
            "french", "german", "deutsch", "spanish", "espanol", "español", "arabic", "arabia", "russian", "portuguese",
            "italian", "italy", "polska", "poland", "hispanic", "latin", "latino", "latinoamerica", "latinoamérica",
            "brasil", "brazil", "deportes", "esportes", "turk", "türk", "hindi", "urdu", "persian", "persiana", "farsi",
            "indonesia", "mongolia", "chile", "argentina", "colombia", "mexico", "méxico", "cine", "accion", "acción",
            "comedia", "novelas", "infantil", "reino", "nuevos", "misterios", "minuto", "estrellas", "terror",
            "handbold", "handboll"));
    private static final Set<String> FOREIGN_COUNTRIES = new HashSet<>(Arrays.asList(
            "germany", "france", "spain", "sweden", "denmark", "norway", "netherlands", "austria", "switzerland",
            "portugal", "finland", "belgium", "greece", "turkey"));
    private static final Set<String> OTHER_SUFFIX = new HashSet<>(Arrays.asList(
            "dk", "no", "se", "de", "es", "br", "fr", "it", "pl", "ru", "pt"));
    private static final Set<String> ENGLISH_WORDS = new HashSet<>(Arrays.asList(
            "english", "uk", "us", "usa", "au", "gb", "world", "international"));
    private static final Set<String> ENGLISH_COUNTRIES = new HashSet<>(Arrays.asList("uk", "gb", "us", "ca", "au", "ie", "nz", "sg"));
    private static final String[] BRANDS = {"pluto tv", "rakuten tv", "samsung", "plex", "roku", "filmrise", "bbc", "pbs", "nbc",
            "cbs", "abc news", "bloomberg", "red bull", "dazn", "fifa+", "nhk", "dw ", "france 24", "al jazeera", "euronews"};

    static boolean neverPromote(Channel ch) {
        String n = lower(ch.name);
        for (String f : FREE_EXCEPTIONS) if (n.startsWith(f)) return false;
        for (String p : NEVER_PROMOTE) if (n.startsWith(p)) return true;
        return n.equals("tennis channel");
    }

    /** Words of a name (letters, digits, + and &), without regular expressions (they're slow on this box). */
    private static java.util.List<String> words(String lowerName) {
        java.util.List<String> out = new java.util.ArrayList<>();
        int start = -1;
        for (int i = 0; i <= lowerName.length(); i++) {
            char c = i < lowerName.length() ? lowerName.charAt(i) : ' ';
            boolean part = Character.isLetterOrDigit(c) || c == '+' || c == '&';
            if (part && start < 0) start = i;
            if (!part && start >= 0) {
                out.add(lowerName.substring(start, i));
                start = -1;
            }
        }
        return out;
    }

    /** True when the name itself says it's another language's feed ("FIFA+ French", "Red Bull TV DE"). */
    static boolean otherLanguageName(Channel ch) {
        java.util.List<String> w = words(lower(ch.name));
        for (String x : w) if (OTHER_WORDS.contains(x)) return true;
        // "Avatar (Germany)" is the German feed; "Pluto TV Space (Germany) GB" is its British one.
        String last = w.isEmpty() ? "" : w.get(w.size() - 1);
        if (!last.equals("gb") && !last.equals("uk") && !last.equals("us")) {
            String n = lower(ch.name);
            for (String x : FOREIGN_COUNTRIES) if (n.contains("(" + x + ")")) return true;
        }
        return w.size() > 1 && OTHER_SUFFIX.contains(w.get(w.size() - 1));
    }

    /** 0 = English or Thai, 1 = unknown, 2 = another language. */
    static Context factsContext; // set once by Home, so ranking can use iptv-org's language facts

    static int language(Channel ch) {
        String langs = factsContext == null ? null : ChannelFacts.languages(factsContext, ch);
        if (langs != null) { // the database knows: English or Thai feeds are "ours", others go below
            return langs.contains("eng") || langs.contains("tha") ? 0 : 2;
        }
        if (otherLanguageName(ch)) return 2;
        java.util.List<String> w = words(lower(ch.name));
        if (ch.country != null && (ch.country.equals("th") || ENGLISH_COUNTRIES.contains(ch.country))) return 0;
        for (String x : w) if (ENGLISH_WORDS.contains(x)) return 0;
        String list = ch.categoryId == null ? "" : ch.categoryId;
        if (list.startsWith("countries/")) return 0; // from the Thai, UK or US list
        return ch.country == null ? 1 : 2;
    }

    /** Local affiliates and numbered copies: "Fox (United States) KTTV", "CBS News Boston", "ABC News Live 7". */
    static boolean local(String n) {
        if (n.startsWith("cbs news ") && !n.equals("cbs news 24/7")) return true;
        if (n.startsWith("abc news live ") || n.contains("(united states) ")) return true;
        if (n.startsWith("nbc ") || n.startsWith("abc ") || n.startsWith("pbs ") || n.startsWith("fox ")) {
            String last = n.substring(n.lastIndexOf(' ') + 1);
            if (callSign(last)) return true;
        }
        int sp = n.lastIndexOf(' ');
        String last = sp < 0 ? n : n.substring(sp + 1);
        return callSign(last) || (n.contains("channel ") && last.length() <= 3 && !last.isEmpty() && Character.isDigit(last.charAt(0)));
    }

    /** US station call sign: K or W + 2-3 letters, optional -TV/-CD/-LD ("ktvu", "wjbk", "wbts-cd"). */
    private static boolean callSign(String w) {
        int dash = w.indexOf('-');
        String core = dash > 0 ? w.substring(0, dash) : w;
        if (core.length() < 3 || core.length() > 4 || (core.charAt(0) != 'k' && core.charAt(0) != 'w')) return false;
        for (int i = 0; i < core.length(); i++) if (core.charAt(i) < 'a' || core.charAt(i) > 'z') return false;
        return dash < 0 || w.endsWith("-tv") || w.endsWith("-cd") || w.endsWith("-ld");
    }

    private static boolean brand(String n) {
        for (String b : BRANDS) if (n.startsWith(b)) return true;
        return n.endsWith("pluto tv");
    }

    /** Position in the genre's top list, or -1. */
    static int top(int genre, Channel ch) {
        String[] p = TOP[genre];
        if (p == null) return -1;
        String n = lower(ch.name);
        for (int i = 0; i < p.length; i++) {
            String pat = p[i];
            if (pat.startsWith("=") ? n.equals(pat.substring(1)) : (n.equals(pat) || n.startsWith(pat + " "))) return i;
        }
        return -1;
    }

    /** The part of the score for channels outside the top lists (language, re-stream, local copy, brand, listings). */
    static int base(Channel ch) {
        String n = lower(ch.name);
        int s = 1_000 + language(ch) * 600;
        if (neverPromote(ch)) s += 50_000;
        if (local(n)) s += 300;
        if (brand(n)) s -= 150;
        if (!Guide.has(ch)) s += 60;
        return s;
    }

    /**
     * Ranking score inside a genre: lower is better; ties keep list order. A hand-picked channel keeps its
     * place in the top list (0-999) unless its name marks another language's feed; the country in its id
     * isn't used for those ("FUEL TV" has a Portuguese id but broadcasts in English).
     */
    /** Glow ▸ Settings ▸ "Thai channels first": Thai broadcasters lead Entertainment ahead of the English picks. */
    static volatile boolean thaiFirst;

    static int score(int genre, Channel ch, int base) {
        int t = top(genre, ch);
        if (thaiFirst && genre == ENT && t >= 0 && "th".equals(ch.country)) return t * 10 - 5_000;
        if (t >= 0) return t * 10 + (otherLanguageName(ch) ? 1_200 : 0);
        return base;
    }

    static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
    }
}
