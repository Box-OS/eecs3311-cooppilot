package ca.yorku.cooppilot.llm;

import ca.yorku.cooppilot.domain.PostingAnalysis;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.service.LatexText;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Asks the LLM which bullets to remove or shorten so the resume fits, judged against the posting.
 * Validation rejects edits to unknown bullets and shortened text with numbers not in the original.
 */
public class SmartCondenseTask extends LlmTask<SmartCondenseTask.Plan> {

    public record Edit(String blockId, int index, String text) {}

    /** remove = bullets to delete; shorten = bullets to replace with shorter text. */
    public record Plan(List<Edit> remove, List<Edit> shorten, String reason) {}

    private final Resume resume;
    private final PostingAnalysis analysis;
    private final double pageFraction;

    public SmartCondenseTask(LLMClient llm, Resume resume, PostingAnalysis analysis, double pageFraction) {
        super(llm, 1);
        this.resume = resume;
        this.analysis = analysis;
        this.pageFraction = pageFraction;
    }

    @Override
    protected LlmRequest buildRequest() {
        StringBuilder sb = new StringBuilder();
        sb.append("This resume fills ").append(Math.round(pageFraction * 100)).append("% of a page; it must be at most 98%.\n")
          .append("Posting keywords: ").append(analysis.keywords().stream().map(k -> k.term()).collect(Collectors.joining(", ")))
          .append("\nRemove or shorten the bullets that matter least for this posting. Never change facts or numbers; ")
          .append("jobs give way last; keep at least one bullet per job.\nBULLETS:\n");
        for (ResumeBlock b : resume.getBlocks()) {
            for (int i = 0; i < b.getBullets().size(); i++) {
                sb.append(b.getId()).append(" #").append(i).append(" [").append(b.getSection()).append(" ").append(b.getTitle())
                  .append("] ").append(LatexText.plain(b.getBullets().get(i))).append('\n');
            }
        }
        sb.append("""
                Reply with JSON only:
                {"remove":[{"block_id":"...","index":0}],
                 "shorten":[{"block_id":"...","index":1,"text":"shorter LaTeX bullet"}],
                 "reason":"one sentence"}""");
        return LlmRequest.simple("You condense resumes to one page.", sb.toString(), 1500);
    }

    @Override
    protected Plan parse(LlmResponse response) {
        JsonNode j = JsonReply.object(response.text());
        return new Plan(edits(j.get("remove")), edits(j.get("shorten")), j.path("reason").asText(""));
    }

    @Override
    protected List<String> validate(Plan p) {
        List<String> problems = new ArrayList<>();
        Map<String, ResumeBlock> byId = resume.getBlocks().stream().collect(Collectors.toMap(ResumeBlock::getId, b -> b));
        for (Edit e : p.remove()) {
            if (!exists(byId, e)) problems.add("remove: no bullet " + e.blockId() + " #" + e.index());
        }
        for (Edit e : p.shorten()) {
            if (!exists(byId, e)) { problems.add("shorten: no bullet " + e.blockId() + " #" + e.index()); continue; }
            String old = LatexText.plain(byId.get(e.blockId()).getBullets().get(e.index()));
            for (String n : numbers(LatexText.plain(e.text()))) {
                if (!old.contains(n)) problems.add("shorten " + e.blockId() + " #" + e.index() + " adds the number " + n);
            }
        }
        if (p.remove().isEmpty() && p.shorten().isEmpty()) problems.add("no edits proposed");
        return problems;
    }

    private static boolean exists(Map<String, ResumeBlock> byId, Edit e) {
        ResumeBlock b = byId.get(e.blockId());
        return b != null && e.index() >= 0 && e.index() < b.getBullets().size();
    }

    private static List<String> numbers(String s) {
        List<String> out = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(s);
        while (m.find()) out.add(m.group());
        return out;
    }

    private static List<Edit> edits(JsonNode arr) {
        List<Edit> out = new ArrayList<>();
        if (arr != null) for (JsonNode e : arr) {
            out.add(new Edit(e.path("block_id").asText(), e.path("index").asInt(-1), e.path("text").asText("")));
        }
        return out;
    }
}
