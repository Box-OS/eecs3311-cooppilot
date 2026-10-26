package ca.yorku.cooppilot.persistence;

import ca.yorku.cooppilot.domain.EntryKind;
import ca.yorku.cooppilot.domain.EntryUse;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.domain.Section;
import ca.yorku.cooppilot.domain.VaultEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SqliteRepositoryTest {

    private SqliteVaultRepository vault;
    private SqliteResumeRepository resumes;

    @BeforeEach
    void setUp() throws Exception {
        SingleConnectionDataSource ds = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        String schema = Files.readString(Path.of("src/main/resources/schema.sql"));
        for (String stmt : schema.split(";")) {
            if (!stmt.isBlank()) jdbc.execute(stmt);
        }
        vault = new SqliteVaultRepository(jdbc);
        resumes = new SqliteResumeRepository(jdbc);
    }

    @Test
    void vaultEntryRoundTrips() {
        VaultEntry e = new VaultEntry(null, "Job tracker web app", EntryKind.PROJECT, EntryUse.ALL,
                "2026-09", Set.of("JavaScript", "LaTeX"), "Built a browser-based co-op tracker.");
        vault.save(e);

        VaultEntry loaded = vault.findById(e.getId()).orElseThrow();
        assertEquals("Job tracker web app", loaded.getTitle());
        assertEquals(EntryKind.PROJECT, loaded.getKind());
        assertEquals(Set.of("javascript", "latex"), loaded.getTags());
        assertEquals(1, vault.findByKind(EntryKind.PROJECT).size());
        assertTrue(vault.findByKind(EntryKind.SKILL).isEmpty());
    }

    @Test
    void saveReplacesExistingRowAndDeleteRemovesIt() {
        VaultEntry e = VaultEntry.skill("Docker");
        vault.save(e);
        e.setBody("Containerized a Spring app");
        vault.save(e);
        assertEquals(1, vault.count());
        assertEquals("Containerized a Spring app", vault.findAll().get(0).getBody());

        vault.delete(e.getId());
        assertTrue(vault.findById(e.getId()).isEmpty());
    }

    @Test
    void resumeWithBlocksRoundTrips() {
        ResumeBlock job = new ResumeBlock(null, Section.EXPERIENCE, "Peer Tutor", "York University",
                YearMonth.of(2025, 9), null, List.of("Tutored 20+ students in Java"), "\\resumeSubheading{...}");
        job.addTag("java");
        Resume r = new Resume(null, "CV_General", "software", Resume.Kind.ORIGINAL, List.of(job), "\\documentclass...");
        resumes.save(r);

        Resume loaded = resumes.findByName("CV_General").orElseThrow();
        ResumeBlock b = loaded.getBlocks().get(0);
        assertEquals("Peer Tutor", b.getTitle());
        assertEquals(YearMonth.of(2025, 9), b.getStartDate());
        assertNull(b.getEndDate(), "Present is stored as no end date");
        assertEquals(List.of("Tutored 20+ students in Java"), b.getBullets());
        assertEquals(Set.of("java"), b.getTags());
    }
}
