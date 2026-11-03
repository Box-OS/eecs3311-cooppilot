package ca.yorku.cooppilot.agent.tools;

import ca.yorku.cooppilot.agent.AgentSession;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.service.CondenseStrategy;
import ca.yorku.cooppilot.service.LatexAssembler;
import ca.yorku.cooppilot.service.PageEstimator;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/** Fits the draft to one page with the chosen condense strategy (rules = free, smart = 1 LLM request). */
@Component
public class CondenseTool extends AgentTool {

    private final Map<CondenseStrategy.Mode, CondenseStrategy> strategies;
    private final PageEstimator pages;
    private final LatexAssembler assembler;

    public CondenseTool(Map<CondenseStrategy.Mode, CondenseStrategy> strategies, PageEstimator pages, LatexAssembler assembler) {
        this.strategies = strategies;
        this.pages = pages;
        this.assembler = assembler;
    }

    public String name() { return "condense"; }

    public String description() {
        return "Shrink the draft to one page. mode 'rules' (default, free) cuts the least relevant bullets/projects "
                + "in a fixed order; 'smart' lets the LLM choose what to cut or shorten.";
    }

    public JsonNode inputSchema() {
        return Schemas.object().prop("mode", "string", "'rules' or 'smart'", false).build();
    }

    public ToolResult execute(JsonNode args, AgentSession s) {
        Resume d = s.draft().orElseThrow(() -> new IllegalStateException("no draft yet: call compose_resume first"));
        String m = args != null && args.hasNonNull("mode") ? args.get("mode").asText() : "rules";
        CondenseStrategy.Mode mode = m.toLowerCase(Locale.ROOT).startsWith("smart") ? CondenseStrategy.Mode.SMART : CondenseStrategy.Mode.RULES;
        CondenseStrategy strategy = strategies.get(mode);
        double before = pages.estimate(d);
        Resume out = strategy.condense(d, analysis(s), pages);
        assembler.assemble(out, s.<String>get("baseLatex").orElse(d.getLatex()));
        s.updateDraft(out);
        return ToolResult.ok(String.format("%s: %d%% -> %d%% of a page. %s%nCURRENT DRAFT:%n%s", mode,
                Math.round(before * 100), Math.round(pages.estimate(out) * 100), strategy.lastReport(), describe(out)));
    }
}
