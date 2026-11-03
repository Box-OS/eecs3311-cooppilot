package ca.yorku.cooppilot.agent.tools;

import ca.yorku.cooppilot.agent.AgentSession;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.domain.Section;
import ca.yorku.cooppilot.service.LatexAssembler;
import ca.yorku.cooppilot.service.LatexText;
import ca.yorku.cooppilot.service.ResumeLibrary;
import ca.yorku.cooppilot.service.VaultService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Builds the draft from a saved resume's blocks: which blocks to keep, project order, skills to
 * front-load, and reworded bullets. Rewrites that introduce numbers found nowhere in the student's
 * material are rejected, so the model cannot invent results.
 */
@Component
public class ComposeResumeTool extends AgentTool {

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\[[A-Z]+=[^\\]]+]");

    private final ResumeLibrary library;
    private final VaultService vault;
    private final LatexAssembler assembler;

    public ComposeResumeTool(ResumeLibrary library, VaultService vault, LatexAssembler assembler) {
        this.library = library;
        this.vault = vault;
        this.assembler = assembler;
    }

    public String name() { return "compose_resume"; }

    public String description() {
        return "Create the draft from a saved resume's blocks. keep_block_ids limits the experience/project/leadership "
                + "blocks (education and skills are always kept); project_order orders projects by relevance; "
                + "skills_first moves listed skills to the front of the skills lines; replace_bullets rewords bullets "
                + "(LaTeX allowed, \\\\textbf for the key tool or result). Calling it again starts over from the base.";
    }

    public JsonNode inputSchema() {
        return Schemas.object()
                .prop("base_resume_id", "string", "id of the saved resume to start from", true)
                .stringArray("keep_block_ids", "block ids to keep; omit to keep all", false)
                .stringArray("project_order", "project block ids, most relevant first", false)
                .stringArray("skills_first", "skills to move to the front, e.g. [\"Java\",\"SQL\"]", false)
                .objectArray("replace_bullets", "bullet rewrites", false, "block_id", "index:integer", "text")
                .build();
    }

    public ToolResult execute(JsonNode args, AgentSession s) {
        Resume base = library.byId(args.get("base_resume_id").asText())
                .orElseThrow(() -> new IllegalArgumentException("no saved resume with that id"));
        Map<String, ResumeBlock> blocks = new LinkedHashMap<>();
        for (ResumeBlock b : base.getBlocks()) blocks.put(b.getId(), b.copy());
        List<String> notes = new ArrayList<>();

        List<String> keep = strings(args.get("keep_block_ids"));
        if (!keep.isEmpty()) {
            for (String id : keep) if (!blocks.containsKey(id)) notes.add("ignored unknown block id " + id);
            blocks.values().removeIf(b -> b.getSection() != Section.EDUCATION && b.getSection() != Section.SKILLS
                    && !keep.contains(b.getId()));
        }

        String evidence = evidenceText(base);
        for (JsonNode edit : iter(args.get("replace_bullets"))) {
            ResumeBlock b = blocks.get(edit.path("block_id").asText());
            int i = edit.path("index").asInt(-1);
            String text = edit.path("text").asText("").trim();
            if (b == null) { notes.add("rewrite skipped: unknown block " + edit.path("block_id").asText()); continue; }
            if (i < 0 || i >= b.getBullets().size()) { notes.add("rewrite skipped: " + b.getTitle() + " has no bullet " + i); continue; }
            List<String> invented = inventedNumbers(text, LatexText.plain(b.getBullets().get(i)) + " " + evidence);
            if (!invented.isEmpty()) {
                notes.add("REJECTED rewrite of " + b.getTitle() + " #" + i + ": numbers " + invented
                        + " are not in the student's material; use a placeholder like [N=5] or ask_user");
                continue;
            }
            List<String> bullets = new ArrayList<>(b.getBullets());
            bullets.set(i, text);
            b.setBullets(bullets);
        }

        List<ResumeBlock> ordered = orderProjects(new ArrayList<>(blocks.values()), strings(args.get("project_order")));
        List<String> front = strings(args.get("skills_first"));
        if (!front.isEmpty()) ordered.stream().filter(b -> b.getSection() == Section.SKILLS).forEach(b -> frontLoad(b, front));

        Resume draft = new Resume(null, "draft", base.getFamily(), Resume.Kind.TAILORED, ordered, "");
        assembler.assemble(draft, base.getLatex());
        s.updateDraft(draft);
        s.put("baseLatex", base.getLatex());
        return ToolResult.ok("Draft composed from " + base.getName() + ": " + ordered.size() + " blocks."
                + (notes.isEmpty() ? "" : "\nNotes:\n- " + String.join("\n- ", notes))
                + "\nCURRENT DRAFT:\n" + describe(draft));
    }

    /** Numbers in the new text that appear nowhere in the old bullet or the student's material. */
    static List<String> inventedNumbers(String newText, String evidence) {
        String plain = PLACEHOLDER.matcher(LatexText.plain(newText)).replaceAll(" ");
        List<String> out = new ArrayList<>();
        Matcher m = NUMBER.matcher(plain);
        while (m.find()) {
            String n = m.group();
            if (!Pattern.compile("(?<![\\d.])" + Pattern.quote(n) + "(?![\\d])").matcher(evidence).find()) out.add(n);
        }
        return out;
    }

    private String evidenceText(Resume base) {
        String vaultText = vault.all().stream().map(e -> e.getTitle() + " " + e.getBody() + " " + e.getDate())
                .collect(Collectors.joining(" "));
        String resumeText = base.getBlocks().stream().flatMap(b -> b.getBullets().stream())
                .map(LatexText::plain).collect(Collectors.joining(" "));
        return vaultText + " " + resumeText;
    }

    static List<ResumeBlock> orderProjects(List<ResumeBlock> blocks, List<String> order) {
        if (order.isEmpty()) return blocks;
        List<ResumeBlock> projects = new ArrayList<>(blocks.stream().filter(b -> b.getSection() == Section.PROJECTS).toList());
        projects.sort((a, b) -> Integer.compare(rank(order, a.getId()), rank(order, b.getId())));
        List<ResumeBlock> out = new ArrayList<>(blocks.stream().filter(b -> b.getSection() != Section.PROJECTS).toList());
        out.addAll(projects);
        return out;
    }

    private static int rank(List<String> order, String id) {
        int i = order.indexOf(id);
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    /** "Languages: Java, Python, SQL" with front [SQL] -> "Languages: SQL, Java, Python" (and in the LaTeX source). */
    static void frontLoad(ResumeBlock skills, List<String> front) {
        List<String> lines = new ArrayList<>();
        for (String line : skills.getBullets()) lines.add(reorderLine(line, front));
        skills.setBullets(lines);
        Matcher m = Pattern.compile("(\\\\textbf\\s*\\{[^}]*}\\s*\\{\\s*:\\s*)([^}]*)(})").matcher(skills.getLatex());
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + reorderItems(m.group(2), front) + m.group(3)));
        }
        m.appendTail(sb);
        skills.setLatex(sb.toString());
    }

    private static String reorderLine(String line, List<String> front) {
        int c = line.indexOf(':');
        return c < 0 ? line : line.substring(0, c + 1) + " " + reorderItems(line.substring(c + 1), front);
    }

    private static String reorderItems(String items, List<String> front) {
        List<String> parts = new ArrayList<>(List.of(items.split("\\s*,\\s*"))).stream().map(String::trim)
                .filter(x -> !x.isEmpty()).collect(Collectors.toCollection(ArrayList::new));
        List<String> lowered = front.stream().map(f -> f.toLowerCase(Locale.ROOT)).toList();
        parts.sort((a, b) -> Integer.compare(idx(lowered, a), idx(lowered, b)));
        return String.join(", ", parts);
    }

    private static int idx(List<String> lowered, String item) {
        int i = lowered.indexOf(item.toLowerCase(Locale.ROOT));
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    private static List<String> strings(JsonNode arr) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : iter(arr)) if (!n.asText().isBlank()) out.add(n.asText());
        return out;
    }

    private static Iterable<JsonNode> iter(JsonNode arr) {
        return arr == null || !arr.isArray() ? List.of() : arr;
    }
}
