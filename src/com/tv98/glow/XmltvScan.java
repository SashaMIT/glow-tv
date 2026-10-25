package com.tv98.glow;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A fast, forgiving reader for XMLTV listings files. epg.pw's US file unpacks to ~200 MB for 5,500 channels, of
 * which Glow has ~120: instead of tokenising every element like an XML parser, this looks for each
 * {@code <programme}, reads its channel id, and jumps straight past the block when the channel isn't ours.
 * Only matching programmes have their times, title and description decoded.
 */
final class XmltvScan {
    interface Names {
        /** Glow channel keys for a listings channel's display name, or null. */
        List<String> match(String displayName);
    }

    static final class Stats {
        int programmes, kept, channelsMatched;
    }

    private static final byte[] PROGRAMME = ascii("<programme"), PROGRAMME_END = ascii("</programme>"),
            CHANNEL = ascii("<channel"), CHANNEL_END = ascii("</channel>"), TITLE = ascii("<title"),
            TITLE_END = ascii("</title>"), DESC = ascii("<desc"), DESC_END = ascii("</desc>"),
            NAME = ascii("<display-name"), NAME_END = ascii("</display-name>");

    private byte[] buf = new byte[512 * 1024];
    private int len, pos;
    private final InputStream in;

    private XmltvScan(InputStream in) {
        this.in = in;
    }

    /**
     * Reads the whole stream. {@code idToKeys} maps listings channel ids to Glow channel keys; when {@code names}
     * is given, channels are added to it by display name as they're read. Programmes overlapping [from, to)
     * are added to {@code out} per Glow key.
     */
    static Stats scan(InputStream in, Map<String, List<String>> idToKeys, Names names, long from, long to,
            Map<String, List<Guide.Prog>> out, int descMax) throws IOException {
        return new XmltvScan(in).run(idToKeys, names, from, to, out, descMax);
    }

    private Stats run(Map<String, List<String>> idToKeys, Names names, long from, long to,
            Map<String, List<Guide.Prog>> out, int descMax) throws IOException {
        Stats st = new Stats();
        while (true) {
            int lt = indexOf((byte) '<', pos);
            if (lt < 0) {
                pos = len;
                if (!fill()) break;
                continue;
            }
            pos = lt;
            if (startsWith(PROGRAMME, pos)) {
                int end = need(PROGRAMME_END);
                if (end < 0) break;
                st.programmes++;
                int tagEnd = indexOf((byte) '>', pos);
                String ch = attr(pos, tagEnd, "channel");
                List<String> keys = ch == null ? null : idToKeys.get(ch);
                if (keys != null) {
                    long start = time(attr(pos, tagEnd, "start")), stop = time(attr(pos, tagEnd, "stop"));
                    if (start > 0 && stop > start && stop >= from && start <= to) {
                        String title = text(tagEnd, end, TITLE, TITLE_END);
                        if (title != null && !title.isEmpty()) {
                            String desc = descMax > 0 ? text(tagEnd, end, DESC, DESC_END) : null;
                            Guide.Prog p = new Guide.Prog(start, stop, title, shorten(desc, descMax));
                            for (String k : keys) out.computeIfAbsent(k, x -> new ArrayList<>()).add(p);
                            st.kept++;
                        }
                    }
                }
                pos = end + PROGRAMME_END.length;
            } else if (names != null && startsWith(CHANNEL, pos) && isTagBoundary(pos + CHANNEL.length)) {
                int end = need(CHANNEL_END);
                if (end < 0) break;
                int tagEnd = indexOf((byte) '>', pos);
                String id = attr(pos, tagEnd, "id");
                if (id != null && !idToKeys.containsKey(id)) {
                    String name = text(tagEnd, end, NAME, NAME_END);
                    List<String> keys = name == null ? null : names.match(name);
                    if (keys != null && !keys.isEmpty()) {
                        idToKeys.put(id, keys);
                        st.channelsMatched++;
                    }
                }
                pos = end + CHANNEL_END.length;
            } else {
                pos++;
            }
        }
        return st;
    }

    // ---- buffer ----

    /** Makes sure the element starting at pos is complete in the buffer; returns where {@code endTag} starts, or -1 at end of data. */
    private int need(byte[] endTag) throws IOException {
        int from = pos;
        while (true) {
            int at = indexOf(endTag, from, len);
            if (at >= 0) return at;
            from = Math.max(pos, len - endTag.length);
            int before = pos;
            if (!fill()) return -1;
            from -= before - pos; // fill() may have moved the data down
        }
    }

    /** Moves unread data to the front, grows the buffer if one element is bigger than it, and reads more. */
    private boolean fill() throws IOException {
        if (pos > 0) {
            System.arraycopy(buf, pos, buf, 0, len - pos);
            len -= pos;
            pos = 0;
        }
        if (len == buf.length) {
            byte[] bigger = new byte[buf.length * 2];
            System.arraycopy(buf, 0, bigger, 0, len);
            buf = bigger;
        }
        int n = in.read(buf, len, buf.length - len);
        if (n <= 0) return false;
        len += n;
        return true;
    }

    private int indexOf(byte b, int from) {
        for (int i = from; i < len; i++) if (buf[i] == b) return i;
        return -1;
    }

    private int indexOf(byte[] p, int from, int to) {
        byte first = p[0];
        int last = to - p.length;
        outer:
        for (int i = from; i <= last; i++) {
            if (buf[i] != first) continue;
            for (int j = 1; j < p.length; j++) if (buf[i + j] != p[j]) continue outer;
            return i;
        }
        return -1;
    }

    private boolean startsWith(byte[] p, int at) {
        if (at + p.length > len) return false;
        for (int j = 0; j < p.length; j++) if (buf[at + j] != p[j]) return false;
        return true;
    }

    private boolean isTagBoundary(int at) {
        return at < len && (buf[at] == ' ' || buf[at] == '>' || buf[at] == '\t' || buf[at] == '\n');
    }

    // ---- pieces ----

    /** Value of name="…" inside the start tag [from, to). */
    private String attr(int from, int to, String name) {
        byte[] n = ascii(" " + name + "=\"");
        int at = indexOf(n, from, to);
        if (at < 0) return null;
        int s = at + n.length, e = s;
        while (e < to && buf[e] != '"') e++;
        return new String(buf, s, e - s, StandardCharsets.UTF_8);
    }

    /** Decoded text of the first <tag …>…</tag> within [from, to), or null. */
    private String text(int from, int to, byte[] open, byte[] close) {
        int at = indexOf(open, from, to);
        if (at < 0) return null;
        int gt = at + open.length;
        while (gt < to && buf[gt] != '>') gt++;
        if (gt >= to || buf[gt - 1] == '/') return null;
        int end = indexOf(close, gt + 1, to);
        if (end < 0) return null;
        return unescape(new String(buf, gt + 1, end - gt - 1, StandardCharsets.UTF_8)).trim();
    }

    static String unescape(String s) {
        if (s.indexOf('&') < 0) return s;
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int semi = c == '&' ? s.indexOf(';', i) : -1;
            if (semi < 0 || semi - i > 10) {
                b.append(c);
                continue;
            }
            String e = s.substring(i + 1, semi);
            switch (e) {
                case "amp": b.append('&'); break;
                case "lt": b.append('<'); break;
                case "gt": b.append('>'); break;
                case "quot": b.append('"'); break;
                case "apos": b.append('\''); break;
                default:
                    try {
                        int cp = e.startsWith("#x") || e.startsWith("#X") ? Integer.parseInt(e.substring(2), 16)
                                : e.startsWith("#") ? Integer.parseInt(e.substring(1)) : -1;
                        if (cp < 0) { b.append(c); continue; }
                        b.appendCodePoint(cp);
                    } catch (NumberFormatException ex) {
                        b.append(c);
                        continue;
                    }
            }
            i = semi;
        }
        return b.toString();
    }

    static String shorten(String d, int max) {
        if (d == null || d.isEmpty() || max <= 0) return null;
        d = d.replace('\n', ' ').trim();
        if (d.length() <= max) return d;
        int cut = d.lastIndexOf(' ', max - 1);
        return d.substring(0, cut > max / 2 ? cut : max - 1) + "…";
    }

    /** "20261025000000 +0000" -> epoch ms, without SimpleDateFormat (which is slow on this box). */
    static long time(String t) {
        if (t == null || t.length() < 14) return -1;
        try {
            int y = Integer.parseInt(t.substring(0, 4)), mo = Integer.parseInt(t.substring(4, 6)), d = Integer.parseInt(t.substring(6, 8));
            int h = Integer.parseInt(t.substring(8, 10)), mi = Integer.parseInt(t.substring(10, 12)), s = Integer.parseInt(t.substring(12, 14));
            long days = daysFromCivil(y, mo, d);
            long ms = ((days * 24 + h) * 60 + mi) * 60_000L + s * 1000L;
            int sp = t.indexOf(' ', 14);
            if (sp > 0 && t.length() >= sp + 6) {
                int sign = t.charAt(sp + 1) == '-' ? -1 : 1;
                int oh = Integer.parseInt(t.substring(sp + 2, sp + 4)), om = Integer.parseInt(t.substring(sp + 4, sp + 6));
                ms -= sign * (oh * 60L + om) * 60_000L;
            }
            return ms;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** Days since 1970-01-01 for a proleptic Gregorian date (Howard Hinnant's algorithm). */
    private static long daysFromCivil(int y, int m, int d) {
        y -= m <= 2 ? 1 : 0;
        long era = (y >= 0 ? y : y - 399) / 400;
        long yoe = y - era * 400;
        long doy = (153L * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }
}
