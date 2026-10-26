package ca.yorku.cooppilot.domain;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DomainModelTest {

    @Test
    void keywordMatchesLiterallyWithWordBoundaries() {
        Keyword java = new Keyword("Java", Set.of(), true);
        assertTrue(java.appearsIn("Built a REST API in Java and Spring"));
        assertFalse(java.appearsIn("Wrote JavaScript widgets"), "JavaScript must not count as Java");

        Keyword cpp = new Keyword("C++", Set.of(), false);
        assertTrue(cpp.appearsIn("Systems work in C++ and C"));

        Keyword js = new Keyword("JavaScript", Set.of("JS"), false);
        assertTrue(js.appearsIn("React + JS front end"));
    }

    @Test
    void currentRolesSortBeforeFinishedOnes() {
        ResumeBlock current = new ResumeBlock(null, Section.EXPERIENCE, "Tutor", "", YearMonth.of(2025, 9), null, List.of(), "");
        ResumeBlock old = new ResumeBlock(null, Section.EXPERIENCE, "Cashier", "", YearMonth.of(2023, 1), YearMonth.of(2024, 8), List.of(), "");
        assertTrue(current.isCurrent());
        assertTrue(current.sortDate().isAfter(old.sortDate()));
    }

    @Test
    void resumeDetectsPlaceholdersAndCopiesDeeply() {
        ResumeBlock b = new ResumeBlock(null, Section.PROJECTS, "Tracker", "", null, null,
                List.of("Cut build time by [X=30\\%] using caching"), "");
        Resume r = new Resume(null, "CV_General", "", Resume.Kind.ORIGINAL, List.of(b), "");
        assertTrue(r.hasPlaceholders());

        Resume copy = r.copy();
        copy.getBlocks().get(0).setBullets(List.of("Cut build time using caching"));
        assertTrue(r.hasPlaceholders(), "editing the copy must not change the original");
        assertFalse(copy.hasPlaceholders());
        assertNotEquals(r.getId(), copy.getId());
    }

    @Test
    void sectionRulesAndHeadingMapping() {
        assertEquals(Section.SKILLS, Section.fromHeading("Technical Skills").orElseThrow());
        assertEquals(Section.EXPERIENCE, Section.fromHeading("Work Experience").orElseThrow());
        assertFalse(Section.EDUCATION.allowsBold());
        assertTrue(Section.EXPERIENCE.chronological());
        assertFalse(Section.PROJECTS.chronological());
    }

    @Test
    void entryUseLimitsWhereFactsGo() {
        assertFalse(EntryUse.CONTEXT_ONLY.allowedOnResume());
        assertFalse(EntryUse.CONTEXT_ONLY.allowedInLetter());
        assertTrue(EntryUse.LETTERS_ONLY.allowedInLetter());
        assertEquals("Docker", VaultEntry.skill("Docker").skillName());
    }
}
