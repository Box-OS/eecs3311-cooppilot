package ca.yorku.cooppilot.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A full resume: ordered blocks plus the assembled LaTeX. Names follow CV_Company_Role.
 */
public class Resume {

    /** Unknown numbers are written as placeholders with an estimate, e.g. [N=5] or [X=30\%]. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\[[A-Z]+=[^\\]]+]");

    public enum Kind { ORIGINAL, TAILORED, GENERATED }

    private final String id;
    private String name;
    private String family;
    private Kind kind;
    private final List<ResumeBlock> blocks = new ArrayList<>();
    private String latex;

    public Resume(String id, String name, String family, Kind kind, List<ResumeBlock> blocks, String latex) {
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        this.name = name == null ? "" : name;
        this.family = family == null ? "" : family;
        this.kind = kind == null ? Kind.ORIGINAL : kind;
        if (blocks != null) this.blocks.addAll(blocks);
        this.latex = latex == null ? "" : latex;
    }

    /** Deep copy with a new id; used before every edit so history keeps the old version. */
    public Resume copy() {
        List<ResumeBlock> copies = blocks.stream().map(ResumeBlock::copy).toList();
        return new Resume(null, name, family, kind, copies, latex);
    }

    public List<ResumeBlock> blocksIn(Section s) {
        return blocks.stream().filter(b -> b.getSection() == s).toList();
    }

    public boolean hasPlaceholders() {
        if (PLACEHOLDER.matcher(latex).find()) return true;
        return blocks.stream().flatMap(b -> b.getBullets().stream()).anyMatch(t -> PLACEHOLDER.matcher(t).find());
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFamily() { return family; }
    public void setFamily(String family) { this.family = family; }
    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }
    public List<ResumeBlock> getBlocks() { return List.copyOf(blocks); }
    public void setBlocks(List<ResumeBlock> newBlocks) { blocks.clear(); blocks.addAll(newBlocks); }
    public String getLatex() { return latex; }
    public void setLatex(String latex) { this.latex = latex; }
}
