package ca.yorku.cooppilot.persistence;

import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.domain.Section;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Stores each resume as one row; its blocks are a JSON array column. */
@Repository
public class SqliteResumeRepository extends SqliteRepository<Resume> implements ResumeRepository {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Plain JSON shape of a block (dates as "2025-09" strings). */
    record BlockRow(String id, String section, String title, String subtitle, String start, String end,
                    List<String> bullets, List<String> tags, String latex) {}

    public SqliteResumeRepository(JdbcTemplate jdbc) {
        super(jdbc, "resumes");
    }

    @Override
    public Optional<Resume> findByName(String name) {
        return jdbc.query("SELECT * FROM resumes WHERE name = ?", (rs, n) -> fromRow(rs), name).stream().findFirst();
    }

    @Override
    protected Map<String, Object> toRow(Resume r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.getId());
        row.put("name", r.getName());
        row.put("family", r.getFamily());
        row.put("kind", r.getKind().name());
        row.put("blocks", writeBlocks(r.getBlocks()));
        row.put("latex", r.getLatex());
        return row;
    }

    @Override
    protected Resume fromRow(ResultSet rs) throws SQLException {
        return new Resume(rs.getString("id"), rs.getString("name"), rs.getString("family"),
                Resume.Kind.valueOf(rs.getString("kind")), readBlocks(rs.getString("blocks")), rs.getString("latex"));
    }

    static String writeBlocks(List<ResumeBlock> blocks) {
        List<BlockRow> rows = blocks.stream().map(b -> new BlockRow(b.getId(), b.getSection().name(), b.getTitle(),
                b.getSubtitle(), str(b.getStartDate()), str(b.getEndDate()), b.getBullets(),
                List.copyOf(b.getTags()), b.getLatex())).toList();
        try {
            return JSON.writeValueAsString(rows);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not serialize resume blocks", e);
        }
    }

    static List<ResumeBlock> readBlocks(String json) {
        try {
            List<BlockRow> rows = JSON.readValue(json, new TypeReference<>() {});
            return rows.stream().map(r -> {
                ResumeBlock b = new ResumeBlock(r.id(), Section.valueOf(r.section()), r.title(), r.subtitle(),
                        ym(r.start()), ym(r.end()), r.bullets(), r.latex());
                if (r.tags() != null) r.tags().forEach(b::addTag);
                return b;
            }).toList();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("corrupt resume blocks column", e);
        }
    }

    private static String str(YearMonth ym) {
        return ym == null ? null : ym.toString();
    }

    private static YearMonth ym(String s) {
        return s == null || s.isBlank() ? null : YearMonth.parse(s);
    }
}
