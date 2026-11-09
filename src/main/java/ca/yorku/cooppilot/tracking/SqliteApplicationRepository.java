package ca.yorku.cooppilot.tracking;

import ca.yorku.cooppilot.persistence.SqliteRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class SqliteApplicationRepository extends SqliteRepository<Application> implements ApplicationRepository {

    private static final ObjectMapper JSON = new ObjectMapper();

    record EventRow(String from, String to, String on, String note) {}

    public SqliteApplicationRepository(JdbcTemplate jdbc) {
        super(jdbc, "applications");
    }

    @Override
    protected Map<String, Object> toRow(Application a) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", a.getId());
        row.put("company", a.getCompany());
        row.put("role", a.getRole());
        row.put("posting_id", a.getPostingId());
        row.put("resume_name", a.getResumeName());
        row.put("cover_letter_name", a.getCoverLetterName());
        row.put("status", a.getStatus().name());
        row.put("applied_on", str(a.getAppliedOn()));
        row.put("follow_up_on", str(a.getFollowUpOn()));
        row.put("notes", a.getNotes());
        try {
            row.put("history", JSON.writeValueAsString(a.getHistory().stream()
                    .map(e -> new EventRow(e.from().name(), e.to().name(), e.on().toString(), e.note())).toList()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return row;
    }

    @Override
    protected Application fromRow(ResultSet rs) throws SQLException {
        Application a = new Application(rs.getString("id"), rs.getString("company"), rs.getString("role"),
                rs.getString("posting_id"), rs.getString("resume_name"));
        a.setCoverLetterName(rs.getString("cover_letter_name"));
        a.setAppliedOn(date(rs.getString("applied_on")));
        a.setFollowUpOn(date(rs.getString("follow_up_on")));
        a.setNotes(rs.getString("notes"));
        try {
            List<EventRow> rows = JSON.readValue(rs.getString("history"), new TypeReference<>() {});
            a.restore(Status.valueOf(rs.getString("status")), rows.stream().map(r -> new StatusEvent(
                    Status.valueOf(r.from()), Status.valueOf(r.to()), LocalDate.parse(r.on()), r.note())).toList());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("corrupt history column", e);
        }
        return a;
    }

    private static String str(LocalDate d) {
        return d == null ? null : d.toString();
    }

    private static LocalDate date(String s) {
        return s == null || s.isBlank() ? null : LocalDate.parse(s);
    }
}
