package ca.yorku.cooppilot.persistence;

import ca.yorku.cooppilot.domain.CoverLetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Cover letters keyed by file name (CL_Company_Role); saving the same name replaces it. */
@Repository
public class CoverLetterRepository extends SqliteRepository<CoverLetter> {

    public CoverLetterRepository(JdbcTemplate jdbc) {
        super(jdbc, "cover_letters");
    }

    public Optional<CoverLetter> findByName(String name) {
        return findById(name);
    }

    public List<CoverLetter> findByPosting(String postingId) {
        return jdbc.query("SELECT * FROM cover_letters WHERE posting_id = ?", (rs, n) -> fromRow(rs), postingId);
    }

    @Override
    protected Map<String, Object> toRow(CoverLetter c) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", c.name());
        row.put("posting_id", c.postingId());
        row.put("body", c.body());
        return row;
    }

    @Override
    protected CoverLetter fromRow(ResultSet rs) throws SQLException {
        return new CoverLetter(rs.getString("id"), rs.getString("posting_id"), rs.getString("body"));
    }
}
