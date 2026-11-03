package ca.yorku.cooppilot.agent.tools;

import ca.yorku.cooppilot.agent.AgentSession;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.service.PageEstimator;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.stream.Collectors;

/** How much of one page the draft fills, and which blocks cost the most lines. */
@Component
public class EstimatePageTool extends AgentTool {

    private final PageEstimator pages;

    public EstimatePageTool(PageEstimator pages) {
        this.pages = pages;
    }

    public String name() { return "estimate_page"; }

    public String description() { return "Estimate how much of one page the current draft fills (must be <= 100%)."; }

    public JsonNode inputSchema() { return Schemas.object().build(); }

    public ToolResult execute(JsonNode args, AgentSession s) {
        Resume d = s.draft().orElseThrow(() -> new IllegalStateException("no draft yet: call compose_resume first"));
        double f = pages.estimate(d);
        String biggest = d.getBlocks().stream()
                .sorted(Comparator.comparingDouble(pages::blockLines).reversed()).limit(4)
                .map(b -> b.getTitle() + " " + String.format("%.1f", pages.blockLines(b)) + " lines")
                .collect(Collectors.joining(", "));
        return ToolResult.ok(String.format("Draft fills %d%% of a page (%s). Biggest blocks: %s",
                Math.round(f * 100), f <= 1.0 ? "fits" : "OVER: call condense", biggest));
    }

}
