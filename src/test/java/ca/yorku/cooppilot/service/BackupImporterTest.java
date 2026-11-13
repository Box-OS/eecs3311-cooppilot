package ca.yorku.cooppilot.service;

import ca.yorku.cooppilot.app.CoopPilotApplication;
import ca.yorku.cooppilot.app.CoopPilotFacade;
import ca.yorku.cooppilot.domain.EntryKind;
import ca.yorku.cooppilot.domain.EntryUse;
import ca.yorku.cooppilot.domain.VaultEntry;
import ca.yorku.cooppilot.persistence.ResumeRepository;
import ca.yorku.cooppilot.persistence.VaultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** F01 extension: JSON backup import (vault entries + resumes). */
@SpringBootTest(classes = CoopPilotApplication.class)
class BackupImporterTest {

    @Autowired CoopPilotFacade facade;
    @Autowired VaultRepository vaultRepo;
    @Autowired ResumeRepository resumeRepo;

    @BeforeEach
    void clean() {
        vaultRepo.findAll().forEach(e -> vaultRepo.delete(e.getId()));
        resumeRepo.findAll().forEach(r -> resumeRepo.delete(r.getId()));
    }

    @Test
    void importsVaultEntriesWithKindsAndUses() throws Exception {
        var r = facade.importBackup(Files.readString(Path.of("samples/sample-backup.json")));
        assertEquals(4, r.vaultAdded());
        VaultEntry story = facade.vaultEntries("logistics").get(0);
        assertEquals(EntryKind.ACTIVITY, story.getKind());
        assertEquals(EntryUse.LETTERS_ONLY, story.getUse());
        assertEquals(EntryUse.CONTEXT_ONLY, facade.vaultEntries("Kubernetes").get(0).getUse());
        assertTrue(facade.vaultEntries("Flutter").get(0).getTags().contains("flutter"));

        assertEquals(0, facade.importBackup(Files.readString(Path.of("samples/sample-backup.json"))).vaultAdded(),
                "importing twice adds nothing");
    }

    @Test
    void importsResumesIntoTheLibraryWithoutDuplicatingTheVault() throws Exception {
        String tex = Files.readString(Path.of("samples/sample-resume.tex"));
        String json = new ObjectMapper().writeValueAsString(Map.of(
                "vault", List.of(),
                "resumes", List.of(Map.of("name", "CV_General", "family", "software", "kind", "original", "latex", tex),
                        Map.of("name", "CV_Broken", "latex", "no sections here"))));
        var r = facade.importBackup(json);
        assertEquals(1, r.resumesImported());
        assertEquals(6, resumeRepo.findByName("CV_General").orElseThrow().getBlocks().size());
        assertEquals(0, vaultRepo.findAll().size(), "resume blocks do not create vault entries in a backup import");
        assertTrue(r.warnings().get(0).contains("CV_Broken"));
    }

    @Test
    void rejectsFilesThatAreNotBackups() {
        assertThrows(IllegalArgumentException.class, () -> facade.importBackup("not json"));
        assertThrows(IllegalArgumentException.class, () -> facade.importBackup("{\"something\": 1}"));
    }
}
