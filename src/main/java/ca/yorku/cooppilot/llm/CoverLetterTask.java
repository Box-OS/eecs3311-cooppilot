package ca.yorku.cooppilot.llm;

import ca.yorku.cooppilot.domain.Posting;
import ca.yorku.cooppilot.domain.PostingAnalysis;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.service.FactGuard;
import ca.yorku.cooppilot.service.ResumeText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * F09: drafts a cover letter from the tailored resume and the letter-safe vault facts. Validation
 * checks length, that it names the company, and that it invents no numbers.
 */
public class CoverLetterTask extends LlmTask<String> {

    static final int MIN_WORDS = 180;
    static final int MAX_WORDS = 450;

    private final Posting posting;
    private final Resume resume;
    private final String letterFacts;
    private final String studentName;

    public CoverLetterTask(LLMClient llm, Posting posting, Resume resume, String letterFacts, String studentName) {
        super(llm, 2);
        this.posting = posting;
        this.resume = resume;
        this.letterFacts = letterFacts;
        this.studentName = studentName;
    }

    @Override
    protected LlmRequest buildRequest() {
        PostingAnalysis a = posting.getAnalysis().orElse(null);
        String needs = a == null ? "" : "MUST-HAVES: " + String.join("; ", a.mustHaves())
                + "\nKEYWORDS: " + a.keywords().stream().map(k -> k.term()).collect(Collectors.joining(", "));
        String prompt = """
                Write a cover letter for this co-op posting, 250-350 words, 3-4 short paragraphs, plain text
                (no LaTeX, no markdown, no placeholders). Start with "Dear Hiring Manager," and end with
                "Sincerely," and the student's name.
                - Use only facts from the RESUME and STUDENT FACTS. Never invent numbers, tools or experiences.
                - Connect 2-3 concrete experiences to the posting's needs; show soft skills through what was done.
                - If the posting names a term or length (e.g. Winter 2027, 4 or 8 months), state availability
                  for exactly that term.

                COMPANY: %s
                ROLE: %s
                TERM: %s
                %s

                POSTING TEXT:
                %s

                RESUME:
                %s

                STUDENT FACTS (letters may use these):
                %s

                STUDENT NAME: %s
                """.formatted(posting.getCompany(), posting.getRole(), posting.getTerm(), needs,
                truncate(posting.getRawText(), 4000), ResumeText.all(resume), truncate(letterFacts, 5000), studentName);
        return LlmRequest.simple("You write concise, specific co-op cover letters.", prompt, 1200);
    }

    @Override
    protected String parse(LlmResponse response) {
        String t = response.text().strip();
        if (t.startsWith("```")) t = t.replaceAll("^```\\w*\\s*", "").replaceAll("\\s*```$", "");
        if (t.isBlank()) throw new IllegalArgumentException("empty letter");
        return t.strip();
    }

    @Override
    protected List<String> validate(String letter) {
        List<String> problems = new ArrayList<>();
        int words = letter.split("\\s+").length;
        if (words < MIN_WORDS) problems.add("too short (" + words + " words, need " + MIN_WORDS + "+)");
        if (words > MAX_WORDS) problems.add("too long (" + words + " words, max " + MAX_WORDS + ")");
        String company = posting.getCompany().trim();
        if (!company.isEmpty() && !letter.toLowerCase(Locale.ROOT).contains(company.toLowerCase(Locale.ROOT).split("\\s+")[0])) {
            problems.add("does not name the company (" + company + ")");
        }
        if (letter.matches("(?s).*\\[[A-Za-z ]+\\].*")) problems.add("contains a [placeholder]");
        List<String> invented = FactGuard.inventedNumbers(letter.replace(posting.getTerm(), ""),
                ResumeText.all(resume) + " " + letterFacts + " " + posting.getRawText());
        if (!invented.isEmpty()) problems.add("invents numbers " + invented);
        return problems;
    }

    private static String truncate(String s, int max) {
        return s == null ? "" : s.length() > max ? s.substring(0, max) : s;
    }
}
