package ca.yorku.cooppilot.app;

import ca.yorku.cooppilot.domain.EntryKind;
import ca.yorku.cooppilot.domain.VaultEntry;
import ca.yorku.cooppilot.persistence.ResumeRepository;
import ca.yorku.cooppilot.persistence.VaultRepository;
import ca.yorku.cooppilot.service.VaultService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** F01 end to end through the facade, with the real SQLite repositories (in-memory database). */
@SpringBootTest(classes = CoopPilotApplication.class)
class ImportResumeTest {

    @Autowired CoopPilotFacade facade;
    @Autowired VaultService vault;
    @Autowired VaultRepository vaultRepo;
    @Autowired ResumeRepository resumeRepo;

    @BeforeEach
    void clean() {
        vaultRepo.findAll().forEach(e -> vaultRepo.delete(e.getId()));
        resumeRepo.findAll().forEach(r -> resumeRepo.delete(r.getId()));
    }

    @Test
    void importSavesResumeAndFillsVault() throws Exception {
        String tex = Files.readString(Path.of("samples/sample-resume.tex"));
        CoopPilotFacade.ImportOutcome out = facade.importResume(tex, "CV_General");

        assertNotNull(out.resume());
        assertEquals(6, out.resume().getBlocks().size());
        assertTrue(resumeRepo.findByName("CV_General").isPresent());

        assertEquals(2, vaultRepo.findByKind(EntryKind.WORK).size());
        assertEquals(2, vaultRepo.findByKind(EntryKind.PROJECT).size());
        assertTrue(vault.skills().contains("Spring Boot"));
        assertTrue(vault.skills().contains("PostgreSQL"), "project tech stacks count as skills");
        assertTrue(out.newVaultEntries() > 0);
    }

    @Test
    void reimportDoesNotDuplicateVaultEntriesOrResumes() throws Exception {
        String tex = Files.readString(Path.of("samples/sample-resume.tex"));
        facade.importResume(tex, "CV_General");
        int entries = vaultRepo.findAll().size();

        CoopPilotFacade.ImportOutcome again = facade.importResume(tex, "CV_General");
        assertEquals(0, again.newVaultEntries());
        assertEquals(entries, vaultRepo.findAll().size());
        assertEquals(1, resumeRepo.findAll().size());
    }

    @Test
    void duplicateSkillsAreMergedCaseInsensitively() {
        vault.addEntry(VaultEntry.skill("Spring Boot"));
        vault.addEntry(VaultEntry.skill("spring boot"));
        assertEquals(1, vaultRepo.findByKind(EntryKind.SKILL).size());
    }

    @Test
    void emptyFileSavesNothing() {
        CoopPilotFacade.ImportOutcome out = facade.importResume("", "CV_Empty");
        assertNull(out.resume());
        assertFalse(out.parse().warnings().isEmpty());
        assertTrue(resumeRepo.findAll().isEmpty());
    }
}
