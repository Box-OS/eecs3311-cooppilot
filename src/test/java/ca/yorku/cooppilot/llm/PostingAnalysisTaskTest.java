package ca.yorku.cooppilot.llm;

import ca.yorku.cooppilot.domain.Posting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PostingAnalysisTaskTest {

    public static final String GOOD = """
            Here you go:
            ```json
            {"company":"Northwind Labs","role":"Software Developer Co-op","term":"Winter 2027",
             "mustHaves":["Java or another OO language","SQL","Git","Strong communication skills"],
             "niceToHaves":["Spring Boot","React","Docker"],
             "keywords":[{"term":"Java","aliases":[],"mustHave":true},
                         {"term":"SQL","aliases":["PostgreSQL"],"mustHave":true},
                         {"term":"java","aliases":[],"mustHave":false},
                         "Git"],
             "documentsRequired":["Resume","Cover Letter","Unofficial Transcript"]}
            ```""";

    private final Posting posting = new Posting(null, "", "", "Northwind Labs posting text", "", null);

    @Test
    void parsesFencedJsonAndDropsDuplicateKeywords() {
        MockLlmClient mock = new MockLlmClient().enqueueText(GOOD);
        PostingAnalysisTask.Result r = new PostingAnalysisTask(mock, posting).run();

        assertEquals("Northwind Labs", r.company());
        assertEquals(List.of("Java", "SQL", "Git"), r.analysis().keywords().stream().map(k -> k.term()).toList());
        assertTrue(r.analysis().keywords().get(1).aliases().contains("PostgreSQL"));
        assertTrue(r.analysis().requiresCoverLetter());
        assertFalse(r.analysis().softSkillFocus(), "1 of 4 must-haves is a soft skill");
        assertEquals(1, mock.received().size());
    }

    @Test
    void retriesWithFeedbackWhenReplyIsInvalid() {
        MockLlmClient mock = new MockLlmClient()
                .enqueueText("Sorry, I can't produce JSON")
                .enqueueText("{\"mustHaves\":[\"Java\"],\"keywords\":[]}")
                .enqueueText(GOOD);
        PostingAnalysisTask.Result r = new PostingAnalysisTask(mock, posting).run();

        assertEquals(3, mock.received().size());
        String thirdPrompt = mock.received().get(2).messages().get(0).content();
        assertTrue(thirdPrompt.contains("keywords is empty"), "the retry tells the model what was wrong");
        assertEquals(3, r.analysis().keywords().size());
    }

    @Test
    void givesUpAfterMaxRetries() {
        MockLlmClient mock = new MockLlmClient().enqueueText("no").enqueueText("no").enqueueText("no");
        LlmTask.LlmTaskException e = assertThrows(LlmTask.LlmTaskException.class,
                () -> new PostingAnalysisTask(mock, posting).run());
        assertEquals(3, e.attempts().size());
    }

    @Test
    void softSkillFocusRule() {
        assertTrue(PostingAnalysisTask.softSkillFocus(List.of("Excellent communication", "Teamwork", "Java")));
        assertTrue(PostingAnalysisTask.softSkillFocus(List.of("Customer service experience", "Excel")));
        assertFalse(PostingAnalysisTask.softSkillFocus(List.of("Java", "SQL", "Git", "Leadership", "Linux")));
    }
}
