package ca.yorku.cooppilot.service.edit;

import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.domain.Section;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResumeDiffTest {

    private static ResumeBlock block(String id, String title, String... bullets) {
        return new ResumeBlock(id, Section.EXPERIENCE, title, "", null, null, List.of(bullets), "");
    }

    @Test
    void reportsRewordedRemovedAddedAndBlockChanges() {
        List<ResumeBlock> before = List.of(block("a", "Tutor", "Tutored \\textbf{20+} students", "Wrote problem sets", "Ran reviews"),
                block("b", "Cashier", "Handled 150+ transactions"));
        List<ResumeBlock> after = List.of(block("a", "Tutor", "Tutored 20+ students in Java", "Ran reviews"),
                block("c", "Hackathon", "Built a bot"));

        List<ResumeDiff.Change> d = ResumeDiff.between(before, after);

        assertTrue(d.contains(new ResumeDiff.Change(ResumeDiff.Kind.BLOCK_REMOVED, "Cashier", "Cashier", "")));
        assertTrue(d.contains(new ResumeDiff.Change(ResumeDiff.Kind.BLOCK_ADDED, "Hackathon", "", "Hackathon")));
        assertTrue(d.contains(new ResumeDiff.Change(ResumeDiff.Kind.REWORDED, "Tutor", "Tutored 20+ students", "Tutored 20+ students in Java")));
        assertTrue(d.contains(new ResumeDiff.Change(ResumeDiff.Kind.REMOVED, "Tutor", "Wrote problem sets", "")),
                "a removed middle bullet is reported as removed, not as two rewordings: " + d);
        assertEquals(4, d.size());
    }

    @Test
    void noChangesWhenEqual() {
        List<ResumeBlock> v = List.of(block("a", "Tutor", "x"));
        assertTrue(ResumeDiff.between(v, List.of(block("a", "Tutor", "x"))).isEmpty());
    }
}
