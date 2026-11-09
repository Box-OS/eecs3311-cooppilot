package ca.yorku.cooppilot.service.edit;

import ca.yorku.cooppilot.app.CoopPilotApplication;
import ca.yorku.cooppilot.app.CoopPilotFacade;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.llm.LLMClient;
import ca.yorku.cooppilot.llm.MockLlmClient;
import ca.yorku.cooppilot.persistence.ResumeRepository;
import ca.yorku.cooppilot.persistence.VaultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** F08: AI revision, manual edits, undo/redo, persistence after each step. */
@SpringBootTest(classes = CoopPilotApplication.class)
class RevisionTest {

    @Autowired CoopPilotFacade facade;
    @Autowired LLMClient llm;
    @Autowired ResumeRepository resumeRepo;
    @Autowired VaultRepository vaultRepo;

    private Resume resume;
    private ResumeBlock tutor;

    @BeforeEach
    void setUp() throws Exception {
        vaultRepo.findAll().forEach(e -> vaultRepo.delete(e.getId()));
        resumeRepo.findAll().forEach(r -> resumeRepo.delete(r.getId()));
        resume = facade.importResume(Files.readString(Path.of("samples/sample-resume.tex")), "CV_Edit").resume();
        tutor = resume.getBlocks().stream().filter(b -> b.getTitle().equals("Peer Tutor")).findFirst().orElseThrow();
    }

    private String bullet(int i) {
        return facade.resume(resume.getId()).getBlocks().stream()
                .filter(b -> b.getId().equals(tutor.getId())).findFirst().orElseThrow().getBullets().get(i);
    }

    @Test
    void aiRevisionIsValidatedAppliedAndUndoable() {
        String original = bullet(1);
        ((MockLlmClient) llm)
                // invents "35" -> rejected, retried with the problem listed
                .enqueueText("{\"rewrite\":[{\"block_id\":\"" + tutor.getId() + "\",\"index\":1,\"text\":\"Wrote 35 problem sets\"}]}")
                .enqueueText("{\"rewrite\":[{\"block_id\":\"" + tutor.getId() + "\",\"index\":1,"
                        + "\"text\":\"Wrote recursion and linked-list practice sets in \\\\textbf{Java} for drop-in sessions\"}],"
                        + "\"summary\":\"tied the second bullet to Java\"}");

        facade.reviseResume(resume.getId(), "make the second tutor bullet about Java");
        assertTrue(bullet(1).contains("\\textbf{Java}"));
        assertTrue(facade.resume(resume.getId()).getLatex().contains("practice sets in \\textbf{Java}"), "LaTeX reassembled and saved");
        assertTrue(facade.revisionLog(resume.getId()).get(0).contains("tied the second bullet to Java"));

        facade.undo(resume.getId());
        assertEquals(original, bullet(1));

        int requests = ((MockLlmClient) llm).received().size();
        facade.redo(resume.getId());
        assertTrue(bullet(1).contains("\\textbf{Java}"));
        assertEquals(requests, ((MockLlmClient) llm).received().size(), "redo does not call the LLM again");
    }

    @Test
    void manualEditsStackAndNewEditClearsRedo() {
        String first = bullet(0);
        facade.replaceBullet(resume.getId(), tutor.getId(), 0, "Tutored first-year students in Java");
        facade.removeBlock(resume.getId(), resume.getBlocks().stream()
                .filter(b -> b.getTitle().equals("Maze Solver")).findFirst().orElseThrow().getId());
        assertEquals(5, facade.resume(resume.getId()).getBlocks().size());
        assertEquals(2, facade.revisionLog(resume.getId()).size());

        assertTrue(facade.undo(resume.getId()).startsWith("Removed Maze Solver"));
        assertEquals(6, facade.resume(resume.getId()).getBlocks().size());
        assertEquals("Maze Solver", facade.resume(resume.getId()).getBlocks().get(4).getTitle(), "back in its old place");

        facade.replaceBullet(resume.getId(), tutor.getId(), 1, "Wrote practice problem sets");
        assertThrows(IllegalStateException.class, () -> facade.redo(resume.getId()), "new edit clears redo");

        facade.undo(resume.getId());
        facade.undo(resume.getId());
        assertEquals(first, bullet(0));
        assertThrows(IllegalStateException.class, () -> facade.undo(resume.getId()));
    }

    @Test
    void badEditTargetsFailFast() {
        assertThrows(IllegalArgumentException.class, () -> facade.replaceBullet(resume.getId(), tutor.getId(), 9, "x"));
        assertThrows(IllegalArgumentException.class, () -> facade.removeBlock(resume.getId(), "nope"));
    }
}
