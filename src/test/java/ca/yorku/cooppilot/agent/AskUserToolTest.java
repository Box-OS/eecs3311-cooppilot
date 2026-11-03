package ca.yorku.cooppilot.agent;

import ca.yorku.cooppilot.agent.tools.AskUserTool;
import ca.yorku.cooppilot.agent.tools.ToolCall;
import ca.yorku.cooppilot.agent.tools.ToolRegistry;
import ca.yorku.cooppilot.agent.tools.ToolResult;
import ca.yorku.cooppilot.app.CoopPilotApplication;
import ca.yorku.cooppilot.domain.EntryKind;
import ca.yorku.cooppilot.domain.Posting;
import ca.yorku.cooppilot.domain.VaultEntry;
import ca.yorku.cooppilot.persistence.VaultRepository;
import ca.yorku.cooppilot.service.VaultService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** F06: ask, file the answer in the vault, never ask the same question twice. */
@SpringBootTest(classes = CoopPilotApplication.class)
class AskUserToolTest {

    @Autowired AskUserTool tool;
    @Autowired VaultService vault;
    @Autowired VaultRepository vaultRepo;

    private final ObjectMapper json = new ObjectMapper();
    private final List<String> asked = new ArrayList<>();

    @BeforeEach
    void clean() {
        vaultRepo.findAll().forEach(e -> vaultRepo.delete(e.getId()));
        vault.addEntry(VaultEntry.of("StudyBuddy", EntryKind.PROJECT, "- Group-study scheduler"));
    }

    private AgentSession session(String answer) {
        QuestionHandler q = question -> { asked.add(question); return Optional.ofNullable(answer); };
        return new AgentSession(new AgentGoal(AgentGoal.GoalType.TAILOR_RESUME,
                new Posting("p", "N", "Dev", "t", "", null), List.of()), q);
    }

    private ToolResult run(String args, AgentSession s) throws Exception {
        return new ToolRegistry(List.of(tool)).execute(new ToolCall("1", "ask_user", json.readTree(args)), s);
    }

    @Test
    void answerIsFiledIntoTheMatchingEntryAndReusedNextTime() throws Exception {
        String args = "{\"question\":\"Which database did StudyBuddy use?\",\"topic\":\"StudyBuddy\"}";
        ToolResult first = run(args, session("PostgreSQL on Supabase"));
        assertTrue(first.ok());
        assertTrue(first.content().contains("PostgreSQL on Supabase"));
        VaultEntry entry = vault.search("StudyBuddy", null).get(0);
        assertTrue(entry.getBody().contains("Which database did StudyBuddy use: PostgreSQL on Supabase"));

        ToolResult second = run(args, session("should not be asked"));
        assertTrue(second.content().startsWith("Already answered before"));
        assertEquals(1, asked.size(), "the student was asked only once");
    }

    @Test
    void newTopicCreatesAnEntry() throws Exception {
        run("{\"question\":\"What did you build at the hackathon?\",\"topic\":\"HackYork 2025\",\"kind\":\"activity\"}",
                session("A bus-arrival Discord bot in Python"));
        VaultEntry e = vault.search("HackYork", null).get(0);
        assertEquals(EntryKind.ACTIVITY, e.getKind());
        assertTrue(e.getBody().contains("Discord bot"));
    }

    @Test
    void noAnswerMeansDoNotGuess() throws Exception {
        ToolResult r = run("{\"question\":\"What was the team size?\"}", session(null));
        assertTrue(r.content().contains("Do not guess"));
        assertEquals(1, vaultRepo.findAll().size(), "nothing filed");
    }
}
