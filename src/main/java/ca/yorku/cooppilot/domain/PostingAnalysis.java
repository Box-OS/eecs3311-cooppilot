package ca.yorku.cooppilot.domain;

import java.util.List;

/** What the LLM extracted from a posting (F02). */
public record PostingAnalysis(List<String> mustHaves,
                              List<String> niceToHaves,
                              List<Keyword> keywords,
                              List<String> documentsRequired,
                              boolean softSkillFocus) {

    public PostingAnalysis {
        mustHaves = List.copyOf(mustHaves);
        niceToHaves = List.copyOf(niceToHaves);
        keywords = List.copyOf(keywords);
        documentsRequired = List.copyOf(documentsRequired);
    }

    public boolean requiresCoverLetter() {
        return documentsRequired.stream().anyMatch(d -> d.toLowerCase().contains("cover"));
    }
}
