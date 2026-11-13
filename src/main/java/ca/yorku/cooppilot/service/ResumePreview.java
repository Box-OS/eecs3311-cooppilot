package ca.yorku.cooppilot.service;

import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.domain.Section;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a readable, PDF-like view of a resume (Jake's template layout) from its blocks, so the student
 * can read the result in the app without compiling LaTeX. All text is HTML-escaped before the few
 * supported LaTeX commands (bold, italic, escapes) are turned into tags.
 */
@Service
public class ResumePreview {

    public record Entry(String title, String subtitle, String dates, List<String> bullets, boolean project) {}

    public record SectionView(String heading, List<Entry> entries, List<String> skillLines) {}

    public record Preview(String name, String contact, List<SectionView> sections) {}

    private static final Pattern CENTER = Pattern.compile("\\\\begin\\{center}(.*?)\\\\end\\{center}", Pattern.DOTALL);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    public Preview build(Resume r) {
        String name = "";
        String contact = "";
        Matcher m = CENTER.matcher(r.getLatex() == null ? "" : r.getLatex());
        if (m.find()) {
            String[] lines = m.group(1).split("\\\\\\\\");
            name = LatexText.plain(lines[0]);
            List<String> rest = new ArrayList<>();
            for (int i = 1; i < lines.length; i++) {
                String t = LatexText.plain(lines[i].replaceAll("\\\\vspace\\{[^}]*}", ""));
                if (!t.isBlank()) rest.add(t);
            }
            contact = String.join("  |  ", rest);
        }
        List<SectionView> sections = new ArrayList<>();
        for (Section s : Section.values()) {
            List<ResumeBlock> in = r.blocksIn(s);
            if (in.isEmpty()) continue;
            if (s.chronological()) in = LatexAssembler.orderReverseChronological(in);
            if (s == Section.SKILLS) {
                List<String> lines = new ArrayList<>();
                for (ResumeBlock b : in) for (String line : b.getBullets()) lines.add(skillLine(line));
                sections.add(new SectionView(LatexAssembler.heading(s), List.of(), lines));
                continue;
            }
            List<Entry> entries = new ArrayList<>();
            for (ResumeBlock b : in) {
                List<String> bullets = b.getBullets().stream().map(LatexText::toHtml).toList();
                entries.add(new Entry(LatexText.toHtml(b.getTitle()), LatexText.toHtml(b.getSubtitle()),
                        dates(b), bullets, s == Section.PROJECTS));
            }
            sections.add(new SectionView(LatexAssembler.heading(s), entries, List.of()));
        }
        return new Preview(name, contact, sections);
    }

    /** "Languages: Java, SQL" -> "<strong>Languages</strong>: Java, SQL" (escaped). */
    static String skillLine(String line) {
        int c = line.indexOf(':');
        if (c < 0) return LatexText.toHtml(line);
        return "<strong>" + LatexText.toHtml(line.substring(0, c)) + "</strong>: " + LatexText.toHtml(line.substring(c + 1));
    }

    static String dates(ResumeBlock b) {
        YearMonth start = b.getStartDate();
        YearMonth end = b.getEndDate();
        if (start == null) return "";
        if (end == null) return MONTH.format(start) + " – Present";
        if (start.equals(end)) return MONTH.format(start);
        return MONTH.format(start) + " – " + MONTH.format(end);
    }
}
