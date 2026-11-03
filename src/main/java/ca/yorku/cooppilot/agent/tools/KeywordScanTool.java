package ca.yorku.cooppilot.agent.tools;

import ca.yorku.cooppilot.agent.AgentSession;
import ca.yorku.cooppilot.domain.Keyword;
import ca.yorku.cooppilot.domain.KeywordReport;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.service.KeywordScanner;
import ca.yorku.cooppilot.service.ResumeLibrary;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/** ATS keyword coverage of the current draft (or a saved resume) against the posting. */
@Component
public class KeywordScanTool extends AgentTool {

    private final KeywordScanner scanner;
    private final ResumeLibrary library;

    public KeywordScanTool(KeywordScanner scanner, ResumeLibrary library) {
        this.scanner = scanner;
        this.library = library;
    }

    public String name() { return "keyword_scan"; }

    public String description() {
        return "Check which posting keywords literally appear on the current draft (or on resume_id). "
                + "'known but not worded' keywords are ones the vault supports that you could add.";
    }

    public JsonNode inputSchema() {
        return Schemas.object().prop("resume_id", "string", "saved resume id; omit to scan the current draft", false).build();
    }

    public ToolResult execute(JsonNode args, AgentSession s) {
        Resume r;
        if (args != null && args.hasNonNull("resume_id")) {
            r = library.byId(args.get("resume_id").asText()).orElseThrow(() -> new IllegalArgumentException("no saved resume with that id"));
        } else {
            r = s.draft().orElseThrow(() -> new IllegalStateException("no draft yet: pass resume_id or call compose_resume first"));
        }
        KeywordReport k = scanner.scan(r, analysis(s));
        return ToolResult.ok(String.format("coverage %d%%%nbacked: %s%nthin (skills line only): %s%nknown but not worded: %s%nmissing (no evidence, do not add): %s",
                Math.round(k.coverage() * 100), terms(k.backed()), terms(k.thin()), terms(k.knownNotWorded()), terms(k.missing())));
    }

    private static String terms(List<Keyword> ks) {
        return ks.isEmpty() ? "-" : ks.stream().map(Keyword::term).collect(Collectors.joining(", "));
    }
}
