package ca.yorku.cooppilot.service;

import ca.yorku.cooppilot.domain.Keyword;
import ca.yorku.cooppilot.domain.PostingAnalysis;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.domain.Section;
import ca.yorku.cooppilot.llm.MockLlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CondenseStrategyTest {

    private Resume big;
    private final PostingAnalysis analysis = new PostingAnalysis(List.of("Java", "SQL"), List.of(),
            List.of(new Keyword("Java", Set.of(), true), new Keyword("SQL", Set.of("PostgreSQL"), true),
                    new Keyword("React", Set.of(), false)),
            List.of("Resume"), false);

    @BeforeEach
    void setUp() throws Exception {
        ParseResult p = new LatexResumeParser().parse(Files.readString(Path.of("samples/sample-resume.tex")));
        List<ResumeBlock> blocks = new ArrayList<>(p.blocks());
        // two extra irrelevant projects with 3 long bullets each
        for (String name : List.of("Pixel Art Editor", "Guitar Tuner")) {
            blocks.add(new ResumeBlock(null, Section.PROJECTS, name, "Lua", null, null, List.of(
                    "Designed a pixel grid with undo history, palette swapping and export to PNG for a personal hobby project",
                    "Wrote a frequency detector that listens to the microphone and shows the nearest note on a dial",
                    "Packaged the app for Windows and shared it with friends who gave feedback on the interface"), ""));
        }
        big = new Resume(null, "CV_Big", "", Resume.Kind.ORIGINAL, blocks, "");
    }

    @Test
    void estimatorGrowsWithContent() {
        PageEstimator pages = new PageEstimator(52);
        double withExtras = pages.estimate(big);
        Resume small = big.copy();
        small.setBlocks(small.getBlocks().subList(0, 3));
        assertTrue(pages.estimate(small) < withExtras);
    }

    @Test
    void rulesCutIrrelevantProjectsFirstAndKeepJobs() {
        PageEstimator tight = new PageEstimator(40);
        assertFalse(tight.fitsOnePage(big), "precondition: over one page");
        RuleBasedCondense rules = new RuleBasedCondense();

        Resume out = rules.condense(big, analysis, tight);

        assertTrue(tight.fitsOnePage(out), rules.lastReport());
        List<String> projects = out.blocksIn(Section.PROJECTS).stream().map(ResumeBlock::getTitle).toList();
        assertTrue(projects.contains("StudyBuddy"), "the relevant project survives");
        assertTrue(projects.size() < 4, "something was cut");
        assertTrue(Set.of("StudyBuddy", "Maze Solver", "Pixel Art Editor", "Guitar Tuner").containsAll(projects));
        assertFalse(projects.contains("Pixel Art Editor") && projects.contains("Guitar Tuner"),
                "an irrelevant project goes first");
        assertEquals(2, out.blocksIn(Section.EXPERIENCE).size(), "jobs are never dropped");
        assertTrue(out.blocksIn(Section.EXPERIENCE).stream().allMatch(b -> !b.getBullets().isEmpty()));
        assertEquals(big.getBlocks().size(), 8, "the input is not modified");
        assertTrue(rules.lastReport().contains("dropped project"));
    }

    @Test
    void rulesTrimLeastRelevantBulletsWhenProjectsAreExhausted() {
        PageEstimator veryTight = new PageEstimator(26);
        Resume out = new RuleBasedCondense().condense(big, analysis, veryTight);
        ResumeBlock tutor = out.blocksIn(Section.EXPERIENCE).get(0);
        assertTrue(tutor.getBullets().size() >= 1);
        if (tutor.getBullets().size() == 1) assertTrue(tutor.getBullets().get(0).contains("Java"), "the Java bullet is kept");
        assertTrue(out.blocksIn(Section.PROJECTS).size() >= 1, "at least one project is kept");
    }

    @Test
    void smartCondenseAppliesAValidatedPlanThenFallsBackToRules() {
        Resume r = big.copy();
        ResumeBlock pixel = r.getBlocks().stream().filter(b -> b.getTitle().equals("Pixel Art Editor")).findFirst().orElseThrow();
        ResumeBlock tutor = r.blocksIn(Section.EXPERIENCE).get(0);
        MockLlmClient llm = new MockLlmClient()
                // first plan invents a number in a shortened bullet -> rejected and retried
                .enqueueText("{\"remove\":[],\"shorten\":[{\"block_id\":\"" + tutor.getId() + "\",\"index\":1,\"text\":\"Wrote 50 problem sets\"}]}")
                .enqueueText("{\"remove\":[{\"block_id\":\"" + pixel.getId() + "\",\"index\":2},{\"block_id\":\"" + pixel.getId()
                        + "\",\"index\":1}],\"shorten\":[{\"block_id\":\"" + tutor.getId()
                        + "\",\"index\":1,\"text\":\"Wrote recursion and linked-list practice sets\"}],\"reason\":\"hobby bullets matter least\"}");
        LlmCondense smart = new LlmCondense(llm, new RuleBasedCondense());

        Resume out = smart.condense(r, analysis, new PageEstimator(40));

        assertEquals(2, llm.received().size(), "invalid plan was retried");
        assertTrue(llm.received().get(1).messages().get(0).content().contains("adds the number 50"));
        ResumeBlock newTutor = out.blocksIn(Section.EXPERIENCE).get(0);
        assertEquals("Wrote recursion and linked-list practice sets", newTutor.getBullets().get(1));
        assertTrue(new PageEstimator(40).fitsOnePage(out), smart.lastReport());
        assertTrue(smart.lastReport().startsWith("smart condense: removed 2, shortened 1"));
    }
}
