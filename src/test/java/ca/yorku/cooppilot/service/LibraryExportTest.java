package ca.yorku.cooppilot.service;

import ca.yorku.cooppilot.app.CoopPilotApplication;
import ca.yorku.cooppilot.app.CoopPilotFacade;
import ca.yorku.cooppilot.domain.CoverLetter;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.llm.LLMClient;
import ca.yorku.cooppilot.llm.MockLlmClient;
import ca.yorku.cooppilot.llm.PostingAnalysisTaskTest;
import ca.yorku.cooppilot.persistence.ResumeRepository;
import ca.yorku.cooppilot.persistence.VaultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** F10: reuse as-is, library grouping, rename rules, export to files. */
@SpringBootTest(classes = CoopPilotApplication.class, properties = "cooppilot.export-dir=target/test-exports")
class LibraryExportTest {

    @Autowired CoopPilotFacade facade;
    @Autowired LLMClient llm;
    @Autowired ResumeRepository resumeRepo;
    @Autowired VaultRepository vaultRepo;

    private Resume clean;
    private String postingId;

    @BeforeEach
    void setUp() throws Exception {
        vaultRepo.findAll().forEach(e -> vaultRepo.delete(e.getId()));
        resumeRepo.findAll().forEach(r -> resumeRepo.delete(r.getId()));
        String tex = Files.readString(Path.of("samples/sample-resume.tex"));
        facade.importResume(tex, "CV_WithPlaceholder");
        clean = facade.importResume(tex.replace("by [X=30\\%] ", ""), "CV_General").resume();
        ((MockLlmClient) llm).enqueueText(PostingAnalysisTaskTest.GOOD);
        postingId = facade.analyzePosting(facade.importPosting("Northwind posting", "", "").getId()).getId();
    }

    @Test
    void reuseCopiesUnderThePostingNameAndKeepsTheOriginal() {
        Resume copy = facade.reuseResume(postingId, clean.getId());
        assertEquals("CV_NorthwindLabs_SoftwareDeveloper", copy.getName());
        assertEquals(Resume.Kind.TAILORED, copy.getKind());
        assertNotEquals(clean.getId(), copy.getId());
        assertEquals(clean.getLatex(), copy.getLatex());
        assertTrue(resumeRepo.findByName("CV_General").isPresent(), "original untouched");

        facade.reuseResume(postingId, clean.getId());
        assertEquals(3, resumeRepo.findAll().size(), "reusing again replaces the copy instead of duplicating");
    }

    @Test
    void resumesWithPlaceholdersCannotBeReused() {
        Resume withPh = resumeRepo.findByName("CV_WithPlaceholder").orElseThrow();
        assertThrows(IllegalStateException.class, () -> facade.reuseResume(postingId, withPh.getId()));
    }

    @Test
    void libraryGroupsByFamilyAndRenameRefusesDuplicates() {
        facade.setResumeFamily(clean.getId(), "backend");
        Map<String, List<Resume>> groups = facade.resumeLibrary();
        assertEquals(List.of("CV_General"), groups.get("backend").stream().map(Resume::getName).toList());
        assertTrue(groups.containsKey("general"));
        assertThrows(IllegalArgumentException.class, () -> facade.renameResume(clean.getId(), "CV_WithPlaceholder"));
        facade.renameResume(clean.getId(), "CV_Backend");
        assertEquals("CV_Backend", facade.resume(clean.getId()).getName());
    }

    @Test
    void exportWritesTexAndLetterFiles() throws Exception {
        Path tex = facade.exportResume(clean.getId());
        assertEquals("CV_General.tex", tex.getFileName().toString());
        assertTrue(Files.readString(tex).contains("\\section{Experience}"));

        ExportService e = new ExportService(Path.of("target/test-exports"));
        Path letter = e.writeLetter(new CoverLetter("CL_X_Y", null, "Dear Hiring Manager,"));
        assertEquals("Dear Hiring Manager,", Files.readString(letter));
    }

    @Test
    void fileNamesCannotEscapeTheExportFolder(@TempDir Path dir) {
        ExportService e = new ExportService(dir);
        Path out = e.writeLetter(new CoverLetter("../../etc/evil", null, "x"));
        assertEquals(dir, out.getParent());
        assertEquals("etcevil.txt", out.getFileName().toString());
    }
}
