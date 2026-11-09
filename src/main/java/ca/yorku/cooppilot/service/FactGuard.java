package ca.yorku.cooppilot.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic check that new resume text does not introduce numbers the student never gave. */
public final class FactGuard {

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\[[A-Z]+=[^\\]]+]");

    private FactGuard() {}

    /** Numbers in {@code newText} (placeholders like [N=5] excluded) that appear nowhere in {@code evidence}. */
    public static List<String> inventedNumbers(String newText, String evidence) {
        String plain = PLACEHOLDER.matcher(LatexText.plain(newText)).replaceAll(" ");
        List<String> out = new ArrayList<>();
        Matcher m = NUMBER.matcher(plain);
        while (m.find()) {
            String n = m.group();
            if (!Pattern.compile("(?<![\\d.])" + Pattern.quote(n) + "(?![\\d])").matcher(evidence).find()) out.add(n);
        }
        return out;
    }
}
