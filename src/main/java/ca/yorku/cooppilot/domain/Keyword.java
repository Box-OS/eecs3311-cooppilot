package ca.yorku.cooppilot.domain;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** A posting keyword with the aliases an ATS would also accept (e.g. "JavaScript" / "JS"). */
public record Keyword(String term, Set<String> aliases, boolean mustHave) {

    public Keyword {
        if (term == null || term.isBlank()) throw new IllegalArgumentException("term must not be blank");
        aliases = aliases == null ? Set.of() : Set.copyOf(aliases);
    }

    /** Literal, word-boundary match of the term or any alias, like an ATS (no inference). */
    public boolean appearsIn(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (contains(lower, term)) return true;
        return aliases.stream().anyMatch(a -> contains(lower, a));
    }

    private static boolean contains(String lowerText, String word) {
        String w = Pattern.quote(word.toLowerCase(Locale.ROOT));
        return Pattern.compile("(?<![\\w+#])" + w + "(?![\\w+#])").matcher(lowerText).find();
    }
}
