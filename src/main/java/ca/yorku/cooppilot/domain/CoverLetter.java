package ca.yorku.cooppilot.domain;

/** A drafted cover letter, named CL_Company_Role. */
public record CoverLetter(String name, String postingId, String body) {

    public int wordCount() {
        String t = body == null ? "" : body.trim();
        return t.isEmpty() ? 0 : t.split("\\s+").length;
    }
}
