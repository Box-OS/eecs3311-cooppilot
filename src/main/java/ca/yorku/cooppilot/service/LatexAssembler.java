package ca.yorku.cooppilot.service;

import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.domain.Section;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns resume blocks back into a complete Jake's-template document. Enforces the formatting rules
 * no matter what the LLM asked for: chronological sections in reverse-chronological order, and no
 * bold words inside Education or Technical Skills (skill category labels stay bold).
 */
@Service
public class LatexAssembler {

    private static final Pattern FIRST_SECTION = Pattern.compile("\\\\section\\s*\\{");
    private static final Pattern ITEM_LIST_START = Pattern.compile("\\\\resumeItemListStart");
    private static final Pattern BOLD = Pattern.compile("\\\\textbf\\s*\\{([^{}]*)}(?!\\s*\\{\\s*:)");

    static final String DEFAULT_HEADER = """
            \\documentclass[letterpaper,11pt]{article}
            \\begin{document}
            """;

    /** Assembles using the preamble and contact header of {@code templateLatex} (everything before the first \section). */
    public String assemble(List<ResumeBlock> blocks, String templateLatex) {
        StringBuilder out = new StringBuilder(header(templateLatex)).append('\n');
        for (Section s : Section.values()) {
            List<ResumeBlock> in = blocks.stream().filter(b -> b.getSection() == s).toList();
            if (in.isEmpty()) continue;
            if (s.chronological()) in = orderReverseChronological(in);
            out.append("\\section{").append(heading(s)).append("}\n");
            if (s == Section.SKILLS) {
                out.append(stripBold(s, in.get(0).getLatex())).append("\n\n");
                continue;
            }
            out.append("  \\resumeSubHeadingListStart\n");
            for (ResumeBlock b : in) out.append(stripBold(s, blockLatex(b))).append("\n");
            out.append("  \\resumeSubHeadingListEnd\n\n");
        }
        return out.append("\\end{document}\n").toString();
    }

    public Resume assemble(Resume r, String templateLatex) {
        r.setLatex(assemble(r.getBlocks(), templateLatex));
        return r;
    }

    /** Current roles first, then by latest end date. Stable for equal dates. */
    public static List<ResumeBlock> orderReverseChronological(List<ResumeBlock> blocks) {
        List<ResumeBlock> sorted = new ArrayList<>(blocks);
        sorted.sort(Comparator.comparing(ResumeBlock::sortDate).reversed());
        return sorted;
    }

    /** The block's heading source with its bullets regenerated from {@code getBullets()}. */
    static String blockLatex(ResumeBlock b) {
        String src = b.getLatex();
        Matcher m = ITEM_LIST_START.matcher(src);
        String heading = (m.find() ? src.substring(0, m.start()) : src.replaceAll("\\\\resumeItemListEnd\\s*$", "")).stripTrailing();
        if (b.getBullets().isEmpty()) return heading;
        StringBuilder sb = new StringBuilder(heading).append("\n      \\resumeItemListStart\n");
        for (String bullet : b.getBullets()) sb.append("        \\resumeItem{").append(bullet).append("}\n");
        return sb.append("      \\resumeItemListEnd").toString();
    }

    static String stripBold(Section s, String text) {
        if (s.allowsBold()) return text;
        String prev;
        String t = text;
        do {
            prev = t;
            t = BOLD.matcher(t).replaceAll(mr -> Matcher.quoteReplacement(mr.group(1)));
        } while (!t.equals(prev));
        return t;
    }

    static String header(String template) {
        if (template == null || template.isBlank()) return DEFAULT_HEADER;
        Matcher m = FIRST_SECTION.matcher(template);
        return m.find() ? template.substring(0, m.start()).stripTrailing() : DEFAULT_HEADER;
    }

    static String heading(Section s) {
        return switch (s) {
            case EDUCATION -> "Education";
            case EXPERIENCE -> "Experience";
            case PROJECTS -> "Projects";
            case LEADERSHIP -> "Leadership";
            case SKILLS -> "Technical Skills";
        };
    }
}
