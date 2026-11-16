package ca.yorku.cooppilot.service;

import ca.yorku.cooppilot.app.CoopPilotApplication;
import ca.yorku.cooppilot.domain.EntryKind;
import ca.yorku.cooppilot.domain.EntryUse;
import ca.yorku.cooppilot.domain.Keyword;
import ca.yorku.cooppilot.domain.VaultEntry;
import ca.yorku.cooppilot.persistence.VaultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** F06: answers to the agent's questions are remembered, and a "no" never counts as having the skill. */
@SpringBootTest(classes = CoopPilotApplication.class)
class VaultAnswersTest {

    @Autowired VaultService vault;
    @Autowired VaultRepository repo;

    @BeforeEach
    void clean() {
        repo.findAll().forEach(e -> repo.delete(e.getId()));
    }

    @Test
    void aNoIsRememberedButIsNotEvidence() {
        VaultEntry e = vault.fileAnswer("Have you used Go? If so, in which project?", "No, I haven't", "Go", EntryKind.SKILL);

        assertEquals(VaultService.NOT_USED, e.getTitle());
        assertEquals(EntryUse.CONTEXT_ONLY, e.getUse());
        assertFalse(vault.skills().contains("Go"), "a no must not add the skill");
        assertTrue(vault.answeredAbout(new Keyword("Go", Set.of(), true)::appearsIn), "so it isn't asked again");
        assertEquals("No, I haven't", vault.knownAnswer("Have you used Go? If so, in which project?").orElseThrow());
    }

    @Test
    void aYesGoesOnTheEntryItIsAbout() {
        VaultEntry e = vault.fileAnswer("Have you used TypeScript?", "Yes, in MyTime's frontend", "TypeScript", EntryKind.SKILL);

        assertEquals("TypeScript", e.getTitle());
        assertEquals(EntryUse.ALL, e.getUse());
        assertTrue(vault.answeredAbout(new Keyword("TypeScript", Set.of("TS"), true)::appearsIn));
        assertFalse(vault.answeredAbout(new Keyword("Rust", Set.of(), true)::appearsIn));
    }

    @Test
    void recognisesNoAnswers() {
        for (String no : new String[]{"no", "No.", "nope", "Never used it", "I haven't", "i have not used it", "Not really"}) {
            assertTrue(VaultService.isNo(no), no);
        }
        for (String yes : new String[]{"Yes, in StudyBuddy", "In my notes app", "Node.js, for the backend"}) {
            assertFalse(VaultService.isNo(yes), yes);
        }
    }
}
