package ca.yorku.cooppilot.service;

import ca.yorku.cooppilot.domain.PostingAnalysis;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.llm.LLMClient;
import ca.yorku.cooppilot.llm.SmartCondenseTask;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Strategy pattern — concrete strategy (1 LLM request). The model judges what matters least; the plan
 * is validated by {@link SmartCondenseTask} and applied here. If it is still over, the rule-based
 * strategy finishes the job.
 */
@Service
public class LlmCondense implements CondenseStrategy {

    private final LLMClient llm;
    private final RuleBasedCondense fallback;
    private final ThreadLocal<String> report = ThreadLocal.withInitial(() -> "");

    public LlmCondense(LLMClient llm, RuleBasedCondense fallback) {
        this.llm = llm;
        this.fallback = fallback;
    }

    @Override
    public Resume condense(Resume original, PostingAnalysis a, PageEstimator pages) {
        if (pages.fitsOnePage(original)) {
            report.set("already fits");
            return original.copy();
        }
        SmartCondenseTask.Plan plan = new SmartCondenseTask(llm, original, a, pages.estimate(original)).run();
        Resume r = original.copy();
        Map<String, ResumeBlock> byId = r.getBlocks().stream().collect(Collectors.toMap(ResumeBlock::getId, Function.identity()));
        for (SmartCondenseTask.Edit e : plan.shorten()) {
            List<String> bullets = new ArrayList<>(byId.get(e.blockId()).getBullets());
            bullets.set(e.index(), e.text());
            byId.get(e.blockId()).setBullets(bullets);
        }
        // remove from the highest index down so earlier indexes stay valid
        plan.remove().stream().sorted(Comparator.comparingInt(SmartCondenseTask.Edit::index).reversed()).forEach(e -> {
            List<String> bullets = new ArrayList<>(byId.get(e.blockId()).getBullets());
            if (e.index() < bullets.size()) bullets.remove(e.index());
            byId.get(e.blockId()).setBullets(bullets);
        });
        String msg = "smart condense: removed " + plan.remove().size() + ", shortened " + plan.shorten().size()
                + (plan.reason().isBlank() ? "" : " (" + plan.reason() + ")");
        if (!pages.fitsOnePage(r)) {
            r = fallback.condense(r, a, pages);
            msg += "; then rules: " + fallback.lastReport();
        }
        report.set(msg);
        return r;
    }

    @Override
    public String lastReport() {
        return report.get();
    }
}
