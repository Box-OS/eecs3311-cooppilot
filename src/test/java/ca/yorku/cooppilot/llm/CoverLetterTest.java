package ca.yorku.cooppilot.llm;

import ca.yorku.cooppilot.app.CoopPilotApplication;
import ca.yorku.cooppilot.app.CoopPilotFacade;
import ca.yorku.cooppilot.domain.CoverLetter;
import ca.yorku.cooppilot.domain.EntryKind;
import ca.yorku.cooppilot.domain.EntryUse;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.VaultEntry;
import ca.yorku.cooppilot.persistence.ResumeRepository;
import ca.yorku.cooppilot.persistence.VaultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** F09 through the facade with the mock LLM. */
@SpringBootTest(classes = CoopPilotApplication.class)
class CoverLetterTest {

    @Autowired CoopPilotFacade facade;
    @Autowired LLMClient llm;
    @Autowired ResumeRepository resumeRepo;
    @Autowired VaultRepository vaultRepo;

    private Resume resume;
    private String postingId;

    static String letter(String company, String extra) {
        String para = "I tutor first-year students in Java every week at the Lassonde Tutoring Centre and built StudyBuddy, "
                + "a group-study scheduler with a REST API and PostgreSQL. ";
        return "Dear Hiring Manager,\n\nI am applying for the Software Developer Co-op at " + company + " for Winter 2027. "
                + para.repeat(7) + extra + "\n\nSincerely,\nAlex Chen";
    }

    @BeforeEach
    void setUp() throws Exception {
        vaultRepo.findAll().forEach(e -> vaultRepo.delete(e.getId()));
        resumeRepo.findAll().forEach(r -> resumeRepo.delete(r.getId()));
        resume = facade.importResume(Files.readString(Path.of("samples/sample-resume.tex")), "CV_General").resume();
        facade.addVaultEntry(new VaultEntry(null, "Why Northwind", EntryKind.ACTIVITY, EntryUse.LETTERS_ONLY, "",
                Set.of(), "Uses Northwind's routing app with family logistics business"));
        facade.addVaultEntry(new VaultEntry(null, "Secret context", EntryKind.ACTIVITY, EntryUse.CONTEXT_ONLY, "",
                Set.of(), "PRIVATE-DETAIL-XYZ"));
        ((MockLlmClient) llm).enqueueText(PostingAnalysisTaskTest.GOOD);
        postingId = facade.analyzePosting(facade.importPosting("Northwind Labs posting, Winter 2027", "", "").getId()).getId();
    }

    @Test
    void draftsValidatesAndSavesTheLetter() {
        MockLlmClient mock = (MockLlmClient) llm;
        int before = mock.received().size();
        mock.enqueueText("Dear Hiring Manager, too short.")
            .enqueueText(letter("Northwind Labs", "I improved load time by 75% for 900 users."))
            .enqueueText(letter("Northwind Labs", ""));

        CoverLetter cl = facade.writeCoverLetter(postingId, resume.getId());

        assertEquals("CL_NorthwindLabs_SoftwareDeveloper", cl.name());
        assertTrue(cl.body().startsWith("Dear Hiring Manager,"));
        assertTrue(cl.wordCount() >= 180);
        assertEquals(before + 3, mock.received().size(), "short and number-inventing drafts were retried");
        String retry = mock.received().get(before + 2).messages().get(0).content();
        assertTrue(retry.contains("invents numbers"), retry);
        assertTrue(facade.coverLetter(cl.name()).isPresent());

        String prompt = mock.received().get(before).messages().get(0).content();
        assertTrue(prompt.contains("family logistics business"), "letters-only facts are offered");
        assertFalse(prompt.contains("PRIVATE-DETAIL-XYZ"), "context-only facts never reach the letter prompt");
    }

    @Test
    void draftThatKeepsFailingIsSavedWithItsProblems() {
        // UC08 3a: after the retries the last draft is shown with the problems highlighted
        MockLlmClient mock = (MockLlmClient) llm;
        mock.enqueueText(letter("your company", "")).enqueueText(letter("your company", "")).enqueueText(letter("your company", ""));
        CoopPilotFacade.LetterOutcome out = facade.draftCoverLetter(postingId, resume.getId());
        assertTrue(out.problems().get(0).contains("does not name the company"));
        assertTrue(facade.coverLetter(out.letter().name()).isPresent(), "the draft is kept for editing");
    }

    @Test
    void studentEditsAreSaved() {
        ((MockLlmClient) llm).enqueueText(letter("Northwind Labs", ""));
        CoverLetter cl = facade.writeCoverLetter(postingId, resume.getId());
        facade.saveCoverLetter(cl.name(), "Dear Hiring Manager,\n\nMy own words.\n\nSincerely,\nAlex Chen");
        assertTrue(facade.coverLetter(cl.name()).orElseThrow().body().contains("My own words."));
        assertThrows(IllegalArgumentException.class, () -> facade.saveCoverLetter(cl.name(), "  "));
    }

    @Test
    void unparseableRepliesStillFail() {
        MockLlmClient mock = (MockLlmClient) llm;
        mock.enqueueText("").enqueueText("").enqueueText("");
        assertThrows(LlmTask.LlmTaskException.class, () -> facade.draftCoverLetter(postingId, resume.getId()));
    }
}
