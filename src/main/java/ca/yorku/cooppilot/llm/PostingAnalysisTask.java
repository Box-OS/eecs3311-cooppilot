package ca.yorku.cooppilot.llm;

import ca.yorku.cooppilot.domain.Keyword;
import ca.yorku.cooppilot.domain.Posting;
import ca.yorku.cooppilot.domain.PostingAnalysis;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * F02: asks the LLM to extract requirements, ATS keywords and required documents from a posting.
 * The soft-skill-focus flag is then recomputed deterministically from the must-haves, so the rule
 * does not depend on the model's judgment.
 */
public class PostingAnalysisTask extends LlmTask<PostingAnalysisTask.Result> {

    /** Analysis plus the company/role/term the model read from the text (used when the user left them blank). */
    public record Result(PostingAnalysis analysis, String company, String role, String term) {}

    static final Set<String> SOFT_SKILLS = Set.of("communication", "interpersonal", "teamwork", "team player",
            "collaboration", "customer service", "leadership", "time management", "organization", "problem solving",
            "adaptability", "attention to detail");

    private final Posting posting;

    public PostingAnalysisTask(LLMClient llm, Posting posting) {
        super(llm, 2);
        this.posting = posting;
    }

    @Override
    protected LlmRequest buildRequest() {
        String system = "You analyze co-op job postings for a resume-tailoring tool. Reply with one JSON object only.";
        String prompt = """
                Extract from this posting:
                {"company": "...", "role": "...", "term": "e.g. Winter 2027, 4 months or empty",
                 "mustHaves": ["required qualifications, short phrases"],
                 "niceToHaves": ["assets / preferred qualifications"],
                 "keywords": [{"term": "exact tool/skill word an ATS would scan for", "aliases": ["other spellings"], "mustHave": true}],
                 "documentsRequired": ["Resume", "Cover Letter", "Transcript" ...]}
                Use the posting's own words for keywords (e.g. "JavaScript", "REST APIs"). 5-25 keywords.

                POSTING:
                """ + posting.getRawText();
        return LlmRequest.simple(system, prompt, 1500);
    }

    @Override
    protected Result parse(LlmResponse response) {
        JsonNode j = JsonReply.object(response.text());
        List<String> must = JsonReply.strings(j.get("mustHaves"));
        List<Keyword> keywords = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode k : j.path("keywords")) {
            String term = k.isTextual() ? k.asText() : k.path("term").asText("");
            if (term.isBlank() || !seen.add(term.toLowerCase(Locale.ROOT))) continue;
            keywords.add(new Keyword(term.trim(), new LinkedHashSet<>(JsonReply.strings(k.get("aliases"))),
                    k.path("mustHave").asBoolean(false)));
        }
        PostingAnalysis analysis = new PostingAnalysis(must, JsonReply.strings(j.get("niceToHaves")), keywords,
                JsonReply.strings(j.get("documentsRequired")), softSkillFocus(must));
        return new Result(analysis, j.path("company").asText(""), j.path("role").asText(""), j.path("term").asText(""));
    }

    @Override
    protected List<String> validate(Result r) {
        List<String> problems = new ArrayList<>();
        if (r.analysis().keywords().isEmpty()) problems.add("keywords is empty");
        if (r.analysis().mustHaves().isEmpty() && r.analysis().niceToHaves().isEmpty()) {
            problems.add("no requirements were extracted");
        }
        return problems;
    }

    /** Soft-skill focus when 2+ must-haves, or 40%+ of them, are soft skills. */
    static boolean softSkillFocus(List<String> mustHaves) {
        if (mustHaves.isEmpty()) return false;
        long soft = mustHaves.stream().map(m -> m.toLowerCase(Locale.ROOT))
                .filter(m -> SOFT_SKILLS.stream().anyMatch(m::contains)).count();
        return soft >= 2 || soft >= 0.4 * mustHaves.size();
    }
}
