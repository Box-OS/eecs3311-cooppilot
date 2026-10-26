package ca.yorku.cooppilot.service;

import java.time.Month;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small LaTeX helpers: brace-balanced argument reading, plain-text conversion and date parsing. */
public final class LatexText {

    private static final Pattern MONTH_YEAR = Pattern.compile("([A-Za-z]{3,9})\\.?\\s+(\\d{4})");
    private static final Pattern YEAR_ONLY = Pattern.compile("\\b(\\d{4})\\b");

    private LatexText() {}

    /** Result of reading consecutive {...} groups: the group contents and the index just past the last one. */
    public record Args(List<String> values, int end) {}

    /**
     * Reads up to {@code count} brace groups starting at {@code from}, skipping whitespace and comments
     * between them. Nested braces and escaped \{ \} are handled.
     */
    public static Args readArgs(String s, int from, int count) {
        List<String> out = new ArrayList<>();
        int i = from;
        while (out.size() < count) {
            i = skipSpace(s, i);
            if (i >= s.length() || s.charAt(i) != '{') break;
            int depth = 0;
            int start = i + 1;
            for (; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '\\') { i++; continue; }
                if (c == '{') depth++;
                else if (c == '}' && --depth == 0) break;
            }
            if (depth != 0) break; // unbalanced: stop reading
            out.add(s.substring(start, i));
            i++;
        }
        return new Args(out, i);
    }

    private static int skipSpace(String s, int i) {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) i++;
            else if (c == '%' && (i == 0 || s.charAt(i - 1) != '\\')) {
                while (i < s.length() && s.charAt(i) != '\n') i++;
            } else break;
        }
        return i;
    }

    /** "\textbf{Gitlytics} $|$ \emph{Python}" -> "Gitlytics | Python". */
    public static String plain(String latex) {
        String t = latex;
        for (int pass = 0; pass < 3; pass++) {
            t = t.replaceAll("\\\\(textbf|textit|emph|underline|href\\{[^}]*}|small|scshape)\\s*\\{([^{}]*)}", "$2");
        }
        t = t.replace("$|$", "|").replace("\\&", "&").replace("\\%", "%").replace("\\$", "$")
                .replace("\\#", "#").replace("\\_", "_").replace("---", "—").replace("--", "–");
        t = t.replaceAll("\\\\[a-zA-Z]+\\*?", " ").replace("{", "").replace("}", "");
        return t.replaceAll("\\s+", " ").trim();
    }

    /** True if the text looks like a date range ("Sep. 2025 -- Present", "May 2021"). */
    public static boolean looksLikeDate(String text) {
        String t = plain(text).toLowerCase(Locale.ROOT);
        return t.contains("present") || YEAR_ONLY.matcher(t).find();
    }

    /** Parses "Sep. 2025 -- Present" into [2025-09, null]. Missing parts are null; "Present" means no end. */
    public static YearMonth[] parseRange(String text) {
        String t = plain(text);
        String[] parts = t.split("\\s*[–—-]+\\s*|\\s+to\\s+", 2);
        YearMonth start = parseOne(parts[0]).orElse(null);
        YearMonth end;
        if (parts.length < 2) {
            end = start; // a single date: one-month project
        } else if (parts[1].toLowerCase(Locale.ROOT).contains("present")) {
            end = null;
        } else {
            end = parseOne(parts[1]).orElse(null);
        }
        return new YearMonth[]{start, end};
    }

    static Optional<YearMonth> parseOne(String s) {
        Matcher m = MONTH_YEAR.matcher(s);
        if (m.find()) {
            Optional<Month> month = month(m.group(1));
            if (month.isPresent()) return Optional.of(YearMonth.of(Integer.parseInt(m.group(2)), month.get()));
        }
        Matcher y = YEAR_ONLY.matcher(s);
        return y.find() ? Optional.of(YearMonth.of(Integer.parseInt(y.group(1)), Month.JANUARY)) : Optional.empty();
    }

    private static Optional<Month> month(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.startsWith("sept")) n = "sep";
        for (Month m : Month.values()) {
            if (m.name().toLowerCase(Locale.ROOT).startsWith(n.substring(0, 3))) return Optional.of(m);
        }
        return Optional.empty();
    }
}
