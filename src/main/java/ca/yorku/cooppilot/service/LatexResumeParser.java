package ca.yorku.cooppilot.service;

import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.domain.Section;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a Jake's-template LaTeX resume into {@link ResumeBlock}s (F01). Deterministic: no LLM involved.
 * Recognizes \section{...}, \resumeSubheading{4 args}, \resumeProjectHeading{2 args}, \resumeItem{...}
 * and the "\textbf{Label}{: a, b, c}" skills lines.
 */
@Component
public class LatexResumeParser {

    private static final Pattern SECTION = Pattern.compile("\\\\section\\s*\\{([^}]*)}");
    private static final Pattern HEADING = Pattern.compile("\\\\resume(Subheading|ProjectHeading)\\b");
    private static final Pattern ITEM = Pattern.compile("\\\\resumeItem\\b");
    private static final Pattern SKILL_LINE = Pattern.compile("\\\\textbf\\s*\\{([^}]*)}\\s*\\{\\s*:?\\s*([^}]*)}");
    private static final Pattern END_DOC = Pattern.compile("\\\\end\\{document}");

    public ParseResult parse(String latex) {
        if (latex == null || latex.isBlank()) {
            return new ParseResult(List.of(), List.of(), List.of("The file is empty."));
        }
        String body = stripComments(latex);
        List<ResumeBlock> blocks = new ArrayList<>();
        Set<String> skills = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();

        List<int[]> sections = new ArrayList<>();
        List<String> names = new ArrayList<>();
        Matcher sm = SECTION.matcher(body);
        while (sm.find()) {
            sections.add(new int[]{sm.start(), sm.end()});
            names.add(sm.group(1).trim());
        }
        if (sections.isEmpty()) {
            warnings.add("No \\section{...} headings found: is this a Jake's-template resume?");
        }

        for (int s = 0; s < sections.size(); s++) {
            int from = sections.get(s)[1];
            int to = s + 1 < sections.size() ? sections.get(s + 1)[0] : endOfDocument(body);
            String text = body.substring(from, to);
            Optional<Section> section = Section.fromHeading(names.get(s));
            if (section.isEmpty()) {
                warnings.add("Skipped unknown section \"" + names.get(s) + "\".");
                continue;
            }
            if (section.get() == Section.SKILLS) {
                parseSkills(text, blocks, skills, warnings);
            } else {
                parseEntries(section.get(), text, blocks, warnings);
            }
        }
        return new ParseResult(blocks, new ArrayList<>(skills), warnings);
    }

    private void parseEntries(Section section, String text, List<ResumeBlock> out, List<String> warnings) {
        Matcher hm = HEADING.matcher(text);
        List<Integer> starts = new ArrayList<>();
        while (hm.find()) starts.add(hm.start());
        if (starts.isEmpty()) {
            warnings.add("No entries found under " + section + ".");
            return;
        }
        for (int i = 0; i < starts.size(); i++) {
            int end = i + 1 < starts.size() ? starts.get(i + 1) : text.length();
            String chunk = text.substring(starts.get(i), end);
            parseEntry(section, chunk, warnings).ifPresent(out::add);
        }
    }

    /** Parses one heading command and the bullets that follow it. */
    Optional<ResumeBlock> parseEntry(Section section, String chunk, List<String> warnings) {
        Matcher hm = HEADING.matcher(chunk);
        if (!hm.find()) return Optional.empty();
        boolean project = hm.group(1).equals("ProjectHeading");
        LatexText.Args args = LatexText.readArgs(chunk, hm.end(), project ? 2 : 4);
        int expected = project ? 2 : 4;
        if (args.values().size() < expected) {
            warnings.add("Could not read the heading arguments near: " + snippet(chunk));
            return Optional.empty();
        }
        List<String> a = args.values();

        String title;
        String subtitle;
        String dates;
        Set<String> tags = new LinkedHashSet<>();
        if (project) {
            // {\textbf{Name} $|$ \emph{Tech, Stack}}{Dates}
            String[] parts = a.get(0).split("\\$\\|\\$", 2);
            title = LatexText.plain(parts[0]);
            subtitle = parts.length > 1 ? LatexText.plain(parts[1]) : "";
            for (String t : subtitle.split(",")) if (!t.isBlank()) tags.add(t.trim());
            dates = a.get(1);
        } else if (LatexText.looksLikeDate(a.get(1)) && !LatexText.looksLikeDate(a.get(3))) {
            // experience order: {Role}{Dates}{Company}{Location}
            title = LatexText.plain(a.get(0));
            subtitle = LatexText.plain(a.get(2));
            dates = a.get(1);
        } else {
            // education order: {School}{Location}{Degree}{Dates}
            title = LatexText.plain(a.get(0));
            subtitle = LatexText.plain(a.get(2));
            dates = a.get(3);
        }

        YearMonth[] range = LatexText.parseRange(dates);
        if (range[0] == null) warnings.add("Could not read the dates \"" + LatexText.plain(dates) + "\" for " + title + ".");

        List<String> bullets = new ArrayList<>();
        Matcher im = ITEM.matcher(chunk);
        int pos = args.end();
        while (im.find(pos)) {
            LatexText.Args item = LatexText.readArgs(chunk, im.end(), 1);
            if (item.values().isEmpty()) break;
            bullets.add(item.values().get(0).trim());
            pos = item.end();
        }
        if (bullets.isEmpty() && section != Section.EDUCATION) {
            warnings.add(title + " has no bullet points.");
        }

        String source = chunk.replaceAll("\\s*\\\\resume(SubHeadingListEnd|ItemListEnd)\\s*$", "").strip();
        if (!source.contains("\\resumeItemListEnd") && !bullets.isEmpty()) source += "\n\\resumeItemListEnd";

        ResumeBlock block = new ResumeBlock(null, section, title, subtitle, range[0], range[1], bullets, source);
        tags.forEach(block::addTag);
        return Optional.of(block);
    }

    private void parseSkills(String text, List<ResumeBlock> out, Set<String> skills, List<String> warnings) {
        Matcher m = SKILL_LINE.matcher(text);
        List<String> lines = new ArrayList<>();
        while (m.find()) {
            String label = LatexText.plain(m.group(1));
            String items = LatexText.plain(m.group(2));
            lines.add(label + ": " + items);
            for (String s : items.split(",")) if (!s.isBlank()) skills.add(s.trim());
        }
        if (lines.isEmpty()) {
            warnings.add("Found a skills section but no \\textbf{Label}{: ...} lines.");
            return;
        }
        out.add(new ResumeBlock(null, Section.SKILLS, "Technical Skills", "", null, null, lines, text.strip()));
    }

    private static int endOfDocument(String body) {
        Matcher m = END_DOC.matcher(body);
        return m.find() ? m.start() : body.length();
    }

    /** Removes % comments (but keeps escaped \%). */
    private static String stripComments(String latex) {
        return latex.replaceAll("(?m)(?<!\\\\)%.*$", "");
    }

    private static String snippet(String s) {
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() > 60 ? one.substring(0, 60) + "…" : one;
    }
}
