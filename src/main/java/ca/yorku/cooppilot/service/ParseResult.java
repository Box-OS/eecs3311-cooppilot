package ca.yorku.cooppilot.service;

import ca.yorku.cooppilot.domain.ResumeBlock;

import java.util.List;

/** Output of LatexResumeParser: the blocks it found, the skills listed, and anything it could not read. */
public record ParseResult(List<ResumeBlock> blocks, List<String> skills, List<String> warnings) {

    public ParseResult {
        blocks = List.copyOf(blocks);
        skills = List.copyOf(skills);
        warnings = List.copyOf(warnings);
    }
}
