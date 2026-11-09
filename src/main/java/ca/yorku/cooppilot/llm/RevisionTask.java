package ca.yorku.cooppilot.llm;

import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.service.FactGuard;
import ca.yorku.cooppilot.service.LatexText;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * F08: turns a plain-English request into bullet rewrites/removals. Validation rejects edits to unknown
 * bullets and new numbers that are not in the student's material (vault + resume).
 */
public class RevisionTask extends LlmTask<RevisionTask.Result> {

    public record Edit(String blockId, int index, String text) {}

    public record Result(List<Edit> rewrite, List<Edit> remove, String summary) {}

    private final Resume resume;
    private final String instruction;
    private final String evidence;

    public RevisionTask(LLMClient llm, Resume resume, String instruction, String evidence) {
        super(llm, 1);
        this.resume = resume;
        this.instruction = instruction;
        this.evidence = evidence;
    }

    @Override
    protected LlmRequest buildRequest() {
        StringBuilder sb = new StringBuilder("Revise this resume as the student asks. Only change what the request ")
                .append("covers. Use only facts from the bullets and the STUDENT FACTS; never invent numbers. ")
                .append("Keep bullets natural (strong verb, what was done, result), LaTeX allowed.\n\nREQUEST: ")
                .append(instruction).append("\n\nBULLETS:\n");
        for (ResumeBlock b : resume.getBlocks()) {
            for (int i = 0; i < b.getBullets().size(); i++) {
                sb.append(b.getId()).append(" #").append(i).append(" [").append(b.getSection()).append(" ")
                  .append(b.getTitle()).append("] ").append(b.getBullets().get(i)).append('\n');
            }
        }
        sb.append("\nSTUDENT FACTS:\n").append(evidence.length() > 6000 ? evidence.substring(0, 6000) : evidence)
          .append("""

                Reply with JSON only:
                {"rewrite":[{"block_id":"...","index":0,"text":"new LaTeX bullet"}],
                 "remove":[{"block_id":"...","index":1}],
                 "summary":"what you changed, one sentence"}""");
        return LlmRequest.simple("You edit LaTeX resumes precisely.", sb.toString(), 1500);
    }

    @Override
    protected Result parse(LlmResponse response) {
        JsonNode j = JsonReply.object(response.text());
        return new Result(edits(j.get("rewrite")), edits(j.get("remove")), j.path("summary").asText(""));
    }

    @Override
    protected List<String> validate(Result r) {
        List<String> problems = new ArrayList<>();
        Map<String, ResumeBlock> byId = resume.getBlocks().stream().collect(Collectors.toMap(ResumeBlock::getId, Function.identity()));
        for (Edit e : r.rewrite()) {
            ResumeBlock b = byId.get(e.blockId());
            if (b == null || e.index() < 0 || e.index() >= b.getBullets().size()) {
                problems.add("rewrite: no bullet " + e.blockId() + " #" + e.index());
                continue;
            }
            List<String> invented = FactGuard.inventedNumbers(e.text(),
                    LatexText.plain(b.getBullets().get(e.index())) + " " + evidence);
            if (!invented.isEmpty()) problems.add("rewrite " + e.blockId() + " #" + e.index() + " invents numbers " + invented);
        }
        for (Edit e : r.remove()) {
            ResumeBlock b = byId.get(e.blockId());
            if (b == null || e.index() < 0 || e.index() >= b.getBullets().size()) {
                problems.add("remove: no bullet " + e.blockId() + " #" + e.index());
            }
        }
        if (r.rewrite().isEmpty() && r.remove().isEmpty()) problems.add("no changes proposed");
        return problems;
    }

    private static List<Edit> edits(JsonNode arr) {
        List<Edit> out = new ArrayList<>();
        if (arr != null) for (JsonNode e : arr) {
            out.add(new Edit(e.path("block_id").asText(), e.path("index").asInt(-1), e.path("text").asText("")));
        }
        return out;
    }
}
