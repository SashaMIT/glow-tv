package com.tv98.glow;

import android.content.Context;
import android.content.DialogInterface;
import android.util.LruCache;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Glow's menus in English or Thai (Glow ▸ Settings ▸ Language). The English stays in the code, where it's easiest to
 * read and change; {@code res/raw/lang_th.tsv} maps English phrases to Thai, one per line, so translating Glow is one
 * file. Text is translated where it's shown (every {@link Ui#text} view, toasts, dialogs, the guide's canvas), so the
 * code that builds it doesn't change. A line can have {n} for a number ("Sleep in {n} min"), {t} for a time or date, or
 * {} for a name; longer text is also tried piece by piece between "·", ":" and line breaks. Anything not in the file stays English: channel names,
 * programmes and headlines are the broadcasters' own.
 */
final class L {
    static final String AUTO = "auto", EN = "en", TH = "th";

    private static volatile boolean thai;
    private static Map<String, String> exact;
    private static List<Template> templates;
    private static final LruCache<String, String> done = new LruCache<>(800);
    private static final Pattern SLOT = Pattern.compile("\\{[nt]?\\}");
    private static final String[] SEPARATORS = {"\n", "   ·   ", "  ·  ", " · ", "      ", "     ", ": "};

    private static final class Template {
        final Pattern pattern;
        final String thai;

        Template(Pattern pattern, String thai) {
            this.pattern = pattern;
            this.thai = thai;
        }
    }

    private L() {}

    /** The viewer's choice: auto (the box's language), en or th. */
    static String choice(Context c) {
        return c.getSharedPreferences("display", Context.MODE_PRIVATE).getString("language", AUTO);
    }

    static void init(Context c) {
        String pref = choice(c);
        boolean th = pref.equals(TH) || (pref.equals(AUTO) && Locale.getDefault().getLanguage().equals("th"));
        if (th && exact == null) load(c.getApplicationContext());
        done.evictAll();
        thai = th;
    }

    static void set(Context c, String choice) {
        c.getSharedPreferences("display", Context.MODE_PRIVATE).edit().putString("language", choice).commit();
        init(c);
    }

    static boolean thai() {
        return thai;
    }

    /** Locale for dates and day names. */
    static Locale locale() {
        return thai ? new Locale("th") : Locale.getDefault();
    }

    /** "EEEE d MMMM" in the menu language. */
    static CharSequence date(String pattern, long when) {
        if (!thai) return android.text.format.DateFormat.format(pattern, when);
        return new java.text.SimpleDateFormat(pattern, locale()).format(new java.util.Date(when));
    }

    static String t(String s) {
        if (!thai || s == null || s.isEmpty() || s.length() > 600) return s;
        String hit = done.get(s);
        if (hit != null) return hit;
        String out = translate(s);
        done.put(s, out);
        return out;
    }

    /** t() with {} filled in: f("Sleep in {} min", 30). */
    static String f(String template, Object... parts) {
        String s = t(template);
        for (Object p : parts) {
            int at = s.indexOf("{}");
            if (at < 0) break;
            s = s.substring(0, at) + p + s.substring(at + 2);
        }
        return s;
    }

    private static String translate(String s) {
        // Symbols and spaces in front ("★  ", "◐   ", "⏰  ") and spaces after stay as they are.
        int a = 0, b = s.length();
        while (a < b && !Character.isLetterOrDigit(s.charAt(a)) && "(\"'".indexOf(s.charAt(a)) < 0) a++;
        while (b > a && Character.isWhitespace(s.charAt(b - 1))) b--;
        if (a == b) return s;
        String core = s.substring(a, b), key = core.toLowerCase(Locale.ROOT);
        String th = exact.get(key);
        if (th != null) return s.substring(0, a) + th + s.substring(b);
        for (Template tp : templates) {
            Matcher m = tp.pattern.matcher(core);
            if (!m.matches()) continue;
            String r = tp.thai;
            for (int g = 1; g <= m.groupCount(); g++) {
                int at = r.indexOf("{}");
                if (at < 0) break;
                r = r.substring(0, at) + t(m.group(g)) + r.substring(at + 2);
            }
            return s.substring(0, a) + r + s.substring(b);
        }
        for (String sep : SEPARATORS) {
            if (!s.contains(sep)) continue;
            String[] parts = s.split(Pattern.quote(sep), -1);
            StringBuilder o = new StringBuilder();
            boolean changed = false;
            for (int i = 0; i < parts.length; i++) {
                String p = t(parts[i]);
                changed |= !p.equals(parts[i]);
                if (i > 0) o.append(sep);
                o.append(p);
            }
            return changed ? o.toString() : s;
        }
        return s;
    }

    private static synchronized void load(Context c) {
        if (exact != null) return;
        long t0 = android.os.SystemClock.elapsedRealtime();
        Map<String, String> map = new HashMap<>();
        List<Template> tps = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                c.getResources().openRawResource(R.raw.lang_th), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (line.startsWith("#") || tab <= 0) continue;
                String en = line.substring(0, tab).trim().replace("\\n", "\n"), th = line.substring(tab + 1).trim().replace("\\n", "\n");
                if (en.contains("{")) {
                    // {n} a number, {t} something starting with a digit (a time, a date), {} any words; none of
                    // them runs across a "·" or a line break, so a template can't swallow its neighbours.
                    StringBuilder rx = new StringBuilder();
                    Matcher m = SLOT.matcher(en);
                    int at = 0;
                    while (m.find()) {
                        rx.append(Pattern.quote(en.substring(at, m.start())));
                        rx.append(m.group().equals("{n}") ? "(-?\\d[^\\s·]*)" : m.group().equals("{t}") ? "(\\d[^·\\n]*?)" : "([^·\\n]+?)");
                        at = m.end();
                    }
                    rx.append(Pattern.quote(en.substring(at)));
                    tps.add(new Template(Pattern.compile(rx.toString(), Pattern.CASE_INSENSITIVE), SLOT.matcher(th).replaceAll("{}")));
                } else {
                    map.put(en.toLowerCase(Locale.ROOT), th);
                }
            }
        } catch (Exception e) {
            android.util.Log.w("Glow", "translations: " + e);
        }
        templates = tps;
        exact = map;
        android.util.Log.i("GlowPerf", "Thai menus: " + map.size() + " phrases, " + tps.size() + " patterns in "
                + (android.os.SystemClock.elapsedRealtime() - t0) + " ms");
    }

    /** Every Glow text view: translates plain text as it's set. */
    static final class Text extends TextView {
        Text(Context c) {
            super(c);
        }

        @Override
        public void setText(CharSequence text, BufferType type) {
            // Plain text (String, StringBuilder…) is translated; styled text is left as it is.
            super.setText(text != null && !(text instanceof android.text.Spanned) ? t(text.toString()) : text, type);
        }
    }

    static Toast toast(Context c, CharSequence s, int duration) {
        return Toast.makeText(c, s instanceof String ? t((String) s) : s, duration);
    }

    /** AlertDialog.Builder whose title, message and buttons are translated. */
    static final class Dialog extends android.app.AlertDialog.Builder {
        Dialog(Context c, int theme) {
            super(c, theme);
        }

        @Override
        public android.app.AlertDialog.Builder setTitle(CharSequence s) {
            return super.setTitle(s instanceof String ? t((String) s) : s);
        }

        @Override
        public android.app.AlertDialog.Builder setMessage(CharSequence s) {
            return super.setMessage(s instanceof String ? t((String) s) : s);
        }

        @Override
        public android.app.AlertDialog.Builder setPositiveButton(CharSequence s, DialogInterface.OnClickListener l) {
            return super.setPositiveButton(s instanceof String ? t((String) s) : s, l);
        }

        @Override
        public android.app.AlertDialog.Builder setNegativeButton(CharSequence s, DialogInterface.OnClickListener l) {
            return super.setNegativeButton(s instanceof String ? t((String) s) : s, l);
        }

        @Override
        public android.app.AlertDialog.Builder setNeutralButton(CharSequence s, DialogInterface.OnClickListener l) {
            return super.setNeutralButton(s instanceof String ? t((String) s) : s, l);
        }

        @Override
        public android.app.AlertDialog.Builder setItems(CharSequence[] items, DialogInterface.OnClickListener l) {
            return super.setItems(all(items), l);
        }

        @Override
        public android.app.AlertDialog.Builder setSingleChoiceItems(CharSequence[] items, int checked, DialogInterface.OnClickListener l) {
            return super.setSingleChoiceItems(all(items), checked, l);
        }

        private static CharSequence[] all(CharSequence[] items) {
            if (!thai || items == null) return items;
            CharSequence[] out = new CharSequence[items.length];
            for (int i = 0; i < items.length; i++) out[i] = items[i] instanceof String ? t((String) items[i]) : items[i];
            return out;
        }
    }
}
