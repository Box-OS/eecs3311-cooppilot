package ca.yorku.cooppilot.ui.cli;

import ca.yorku.cooppilot.app.CoopPilotApplication;
import ca.yorku.cooppilot.app.CoopPilotFacade;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.llm.LLMClient;
import ca.yorku.cooppilot.llm.MockLlmClient;
import ca.yorku.cooppilot.llm.PostingAnalysisTaskTest;
import ca.yorku.cooppilot.persistence.ResumeRepository;
import ca.yorku.cooppilot.persistence.VaultRepository;
import ca.yorku.cooppilot.tracking.ApplicationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/** The CLI drives every feature through the facade; --json output parses as JSON. */
@SpringBootTest(classes = CoopPilotApplication.class, properties = "cooppilot.export-dir=target/test-exports")
class CliCommandsTest {

    @Autowired CoopPilotFacade facade;
    @Autowired LLMClient llm;
    @Autowired ResumeRepository resumeRepo;
    @Autowired VaultRepository vaultRepo;
    @Autowired ApplicationRepository appRepo;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void clean() {
        vaultRepo.findAll().forEach(e -> vaultRepo.delete(e.getId()));
        resumeRepo.findAll().forEach(r -> resumeRepo.delete(r.getId()));
        appRepo.findAll().forEach(a -> appRepo.delete(a.getId()));
    }

    record Run(int code, String out) {}

    private Run run(String stdin, String... args) {
        StringWriter sw = new StringWriter();
        int code = new CliCommands(facade, new BufferedReader(new StringReader(stdin))).commandLine(new PrintWriter(sw)).execute(args);
        return new Run(code, sw.toString());
    }

    private JsonNode runJson(String... args) throws Exception {
        String[] withJson = new String[args.length + 1];
        System.arraycopy(args, 0, withJson, 0, args.length);
        withJson[args.length] = "--json";
        Run r = run("", withJson);
        return json.readTree(r.out());
    }

    @Test
    void importAnalyzeFitScanTrackAndDashboard() throws Exception {
        Run imp = run("", "vault", "import", "samples/sample-resume.tex", "--name", "CV_Cli");
        assertEquals(0, imp.code(), imp.out());
        assertTrue(imp.out().contains("Imported 6 blocks as CV_Cli"));
        Resume resume = resumeRepo.findByName("CV_Cli").orElseThrow();

        ((MockLlmClient) llm).enqueueText(PostingAnalysisTaskTest.GOOD);
        JsonNode posting = runJson("posting", "add", "samples/sample-posting.txt", "--analyze");
        String postingId = posting.get("id").asText();
        assertEquals("Northwind Labs", posting.get("company").asText());

        JsonNode fit = runJson("fit", postingId);
        assertEquals(100, fit.get("score").asInt());
        assertEquals("GENERATE", fit.get("strategy").asText(), "sample has a placeholder, so no reuse");

        JsonNode scan = runJson("scan", resume.getId(), postingId);
        assertTrue(scan.get("coverage").asInt() > 0);

        JsonNode app = runJson("app", "add", "--posting", postingId, "--resume", "CV_Cli");
        assertEquals("TO_APPLY", app.get("status").asText());
        Run moved = run("", "app", "status", app.get("id").asText(), "APPLIED", "--note", "portal");
        assertTrue(moved.out().contains("is now APPLIED; follow up on"), moved.out());

        JsonNode dash = runJson("dashboard");
        assertEquals(1, dash.get("totalApplied").asInt());
        assertEquals(7, dash.get("lastSevenDays").size());
        assertTrue(run("", "dashboard").out().contains("Applied 1"));

        assertTrue(run("", "library").out().contains("CV_Cli"));
        assertTrue(run("", "export", resume.getId()).out().contains("CV_Cli.tex"));
    }

    @Test
    void shellKeepsUndoHistoryBetweenCommands() {
        run("", "vault", "import", "samples/sample-resume.tex", "--name", "CV_Shell");
        Resume r = resumeRepo.findByName("CV_Shell").orElseThrow();
        String tutor = r.getBlocks().stream().filter(b -> b.getTitle().equals("Peer Tutor")).findFirst().orElseThrow().getId();
        String script = String.join("\n",
                "edit-bullet " + r.getId() + " " + tutor + " 0 \"Tutored students in Java\"",
                "undo " + r.getId(),
                "redo " + r.getId(),
                "exit") + "\n";

        Run shell = run(script, "shell");

        assertTrue(shell.out().contains("Bullet replaced."));
        assertTrue(shell.out().contains("Undid: Replaced bullet 0 of Peer Tutor"));
        assertTrue(shell.out().contains("Redid: Replaced bullet 0 of Peer Tutor"));
        assertEquals("Tutored students in Java", facade.resume(r.getId()).getBlocks().stream()
                .filter(b -> b.getId().equals(tutor)).findFirst().orElseThrow().getBullets().get(0));
    }

    @Test
    void errorsAreReportedNotThrown() throws Exception {
        Run bad = run("", "app", "status", "missing-id", "OFFER");
        assertEquals(1, bad.code());
        assertTrue(bad.out().startsWith("Error: No application with id"));
        JsonNode err = runJson("fit", "nope");
        assertTrue(err.get("error").asText().contains("No posting"));
        assertTrue(run("", "--help").out().contains("tailor"));
    }

    @Test
    void shellSplitsQuotedArguments() {
        assertArrayEquals(new String[]{"revise", "r1", "make it about Java"},
                CliCommands.ShellCmd.split("revise r1 \"make it about Java\""));
    }
}
