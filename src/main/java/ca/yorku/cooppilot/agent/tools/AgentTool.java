package ca.yorku.cooppilot.agent.tools;

import ca.yorku.cooppilot.agent.AgentSession;
import ca.yorku.cooppilot.domain.PostingAnalysis;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.service.LatexText;

/** Shared helpers for the CoopPilot tools. */
abstract class AgentTool implements Tool {

    static final String DRAFT_KEY = "draft";

    protected static PostingAnalysis analysis(AgentSession s) {
        return s.goal().posting().getAnalysis()
                .orElseThrow(() -> new IllegalStateException("the posting has not been analyzed"));
    }

    /** Compact listing of a resume's blocks with ids and numbered bullets, for the model. */
    protected static String describe(Resume r) {
        StringBuilder sb = new StringBuilder();
        for (ResumeBlock b : r.getBlocks()) {
            sb.append("[").append(b.getId()).append("] ").append(b.getSection()).append(": ").append(b.getTitle());
            if (!b.getSubtitle().isBlank()) sb.append(" | ").append(b.getSubtitle());
            if (b.getStartDate() != null) sb.append(" (").append(b.getStartDate()).append(" to ")
                    .append(b.getEndDate() == null ? "present" : b.getEndDate()).append(")");
            sb.append('\n');
            for (int i = 0; i < b.getBullets().size(); i++) {
                sb.append("   ").append(i).append(". ").append(LatexText.plain(b.getBullets().get(i))).append('\n');
            }
        }
        return sb.toString();
    }
}
