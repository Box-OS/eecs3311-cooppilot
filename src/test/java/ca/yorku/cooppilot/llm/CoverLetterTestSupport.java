package ca.yorku.cooppilot.llm;

/** Valid sample letter text for tests in other packages. */
public final class CoverLetterTestSupport {

    private CoverLetterTestSupport() {}

    public static String letter(String company) {
        return CoverLetterTest.letter(company, "");
    }
}
