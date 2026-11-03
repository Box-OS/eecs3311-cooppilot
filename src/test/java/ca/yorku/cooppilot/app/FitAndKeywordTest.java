package ca.yorku.cooppilot.app;

import ca.yorku.cooppilot.domain.FitReport;
import ca.yorku.cooppilot.domain.Keyword;
import ca.yorku.cooppilot.domain.KeywordReport;
import ca.yorku.cooppilot.domain.Posting;
import ca.yorku.cooppilot.domain.TailorStrategy;
import ca.yorku.cooppilot.domain.VaultEntry;
import ca.yorku.cooppilot.llm.LLMClient;
import ca.yorku.cooppilot.llm.MockLlmClient;
import ca.yorku.cooppilot.llm.PostingAnalysisTaskTest;
import ca.yorku.cooppilot.persistence.PostingRepository;
import ca.yorku.cooppilot.persistence.ResumeRepository;
import ca.yorku.cooppilot.persistence.VaultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** F03 + F04 on the sample resume and sample posting. */
@SpringBootTest(classes = CoopPilotApplication.class)
class FitAndKeywordTest {

    @Autowired CoopPilotFacade facade;
    @Autowired LLMClient llm;
    @Autowired VaultRepository vaultRepo;
    @Autowired ResumeRepository resumeRepo;
    @Autowired PostingRepository postingRepo;

    private Posting posting;
    private String resumeId;

    @BeforeEach
    void setUp() throws Exception {
        vaultRepo.findAll().forEach(e -> vaultRepo.delete(e.getId()));
        resumeRepo.findAll().forEach(r -> resumeRepo.delete(r.getId()));
        resumeId = facade.importResume(Files.readString(Path.of("samples/sample-resume.tex")), "CV_General")
                .resume().getId();
        ((MockLlmClient) llm).enqueueText(PostingAnalysisTaskTest.GOOD);
        posting = facade.analyzePosting(facade.importPosting("Northwind posting", "", "").getId());
    }

    @Test
    void fitScoreMatchesVaultSkills() {
        FitReport fit = facade.analyzeFit(posting.getId());
        // keywords: Java (must, 2), SQL alias PostgreSQL (must, 2), Git (1) -> all in the sample vault
        assertEquals(100, fit.score());
        assertTrue(fit.gaps().isEmpty());
        assertTrue(fit.closestResume().isEmpty(), "the sample has a [X=30%] placeholder, so it is never reused");
        assertEquals(TailorStrategy.GENERATE, fit.strategy());
    }

    @Test
    void gapsListMustHavesFirst() {
        vaultRepo.findAll().stream().filter(e -> e.getTitle().equals("Knows Git"))
                .forEach(e -> vaultRepo.delete(e.getId()));
        vaultRepo.findAll().stream().filter(e -> e.getTitle().equals("Knows SQL"))
                .forEach(e -> vaultRepo.delete(e.getId()));
        // PostgreSQL is still a project tag, so SQL stays matched through its alias
        FitReport fit = facade.analyzeFit(posting.getId());
        assertEquals("Git", fit.gaps().get(0));
        assertTrue(fit.score() < 100);
    }

    @Test
    void keywordScanSeparatesBackedThinKnownMissing() {
        facade.addVaultEntry(VaultEntry.skill("Kubernetes"));
        Posting p = facade.posting(posting.getId());
        var a = p.getAnalysis().orElseThrow();
        var extra = new java.util.ArrayList<>(a.keywords());
        extra.add(new Keyword("Kubernetes", java.util.Set.of(), false));
        extra.add(new Keyword("Rust", java.util.Set.of(), false));
        p.setAnalysis(new ca.yorku.cooppilot.domain.PostingAnalysis(a.mustHaves(), a.niceToHaves(), extra,
                a.documentsRequired(), a.softSkillFocus()));
        postingRepo.save(p);

        KeywordReport r = facade.scanKeywords(resumeId, p.getId());
        assertTrue(names(r.backed()).contains("Java"), "Java appears in the tutor bullet");
        assertTrue(names(r.thin()).contains("Git"), "Git appears only in the skills line");
        assertTrue(names(r.knownNotWorded()).contains("Kubernetes"));
        assertTrue(names(r.missing()).contains("Rust"));
        assertEquals(5, r.total());
    }

    @Test
    void cleanResumeCoveringThePostingIsReused() throws Exception {
        String clean = Files.readString(Path.of("samples/sample-resume.tex")).replace("by [X=30\\%] ", "");
        facade.importResume(clean, "CV_Clean");
        FitReport fit = facade.analyzeFit(posting.getId());
        assertEquals("CV_Clean", fit.closest().resume().getName());
        assertEquals(100, fit.closest().matchPercent());
        assertEquals(TailorStrategy.REUSE, fit.strategy());
    }

    @Test
    void fitBeforeAnalysisIsAnError() {
        Posting raw = facade.importPosting("text", "X", "Y");
        assertThrows(IllegalStateException.class, () -> facade.analyzeFit(raw.getId()));
    }

    private static java.util.List<String> names(java.util.List<Keyword> ks) {
        return ks.stream().map(Keyword::term).toList();
    }
}
