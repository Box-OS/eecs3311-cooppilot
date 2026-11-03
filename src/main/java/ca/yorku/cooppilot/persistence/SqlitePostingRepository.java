package ca.yorku.cooppilot.persistence;

import ca.yorku.cooppilot.domain.Keyword;
import ca.yorku.cooppilot.domain.Posting;
import ca.yorku.cooppilot.domain.PostingAnalysis;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Postings with their analysis stored as a JSON column (null until analyzed). */
@Repository
public class SqlitePostingRepository extends SqliteRepository<Posting> implements PostingRepository {

    private static final ObjectMapper JSON = new ObjectMapper();

    record KeywordRow(String term, List<String> aliases, boolean mustHave) {}

    record AnalysisRow(List<String> mustHaves, List<String> niceToHaves, List<KeywordRow> keywords,
                       List<String> documentsRequired, boolean softSkillFocus) {}

    public SqlitePostingRepository(JdbcTemplate jdbc) {
        super(jdbc, "postings");
    }

    @Override
    protected Map<String, Object> toRow(Posting p) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", p.getId());
        row.put("company", p.getCompany());
        row.put("role", p.getRole());
        row.put("raw_text", p.getRawText());
        row.put("term", p.getTerm());
        row.put("deadline", p.getDeadline().map(LocalDate::toString).orElse(null));
        row.put("analysis", p.getAnalysis().map(SqlitePostingRepository::write).orElse(null));
        return row;
    }

    @Override
    protected Posting fromRow(ResultSet rs) throws SQLException {
        String deadline = rs.getString("deadline");
        Posting p = new Posting(rs.getString("id"), rs.getString("company"), rs.getString("role"),
                rs.getString("raw_text"), rs.getString("term"), deadline == null ? null : LocalDate.parse(deadline));
        String analysis = rs.getString("analysis");
        if (analysis != null) p.setAnalysis(read(analysis));
        return p;
    }

    private static String write(PostingAnalysis a) {
        AnalysisRow row = new AnalysisRow(a.mustHaves(), a.niceToHaves(),
                a.keywords().stream().map(k -> new KeywordRow(k.term(), List.copyOf(k.aliases()), k.mustHave())).toList(),
                a.documentsRequired(), a.softSkillFocus());
        try {
            return JSON.writeValueAsString(row);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static PostingAnalysis read(String json) {
        try {
            AnalysisRow r = JSON.readValue(json, AnalysisRow.class);
            return new PostingAnalysis(r.mustHaves(), r.niceToHaves(),
                    r.keywords().stream().map(k -> new Keyword(k.term(), Set.copyOf(k.aliases()), k.mustHave())).toList(),
                    r.documentsRequired(), r.softSkillFocus());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("corrupt analysis column", e);
        }
    }
}
