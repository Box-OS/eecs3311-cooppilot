package ca.yorku.cooppilot.persistence;

import ca.yorku.cooppilot.domain.EntryKind;
import ca.yorku.cooppilot.domain.EntryUse;
import ca.yorku.cooppilot.domain.VaultEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class SqliteVaultRepository extends SqliteRepository<VaultEntry> implements VaultRepository {

    public SqliteVaultRepository(JdbcTemplate jdbc) {
        super(jdbc, "vault_entries");
    }

    @Override
    public List<VaultEntry> findByKind(EntryKind kind) {
        return jdbc.query("SELECT * FROM vault_entries WHERE kind = ? ORDER BY rowid", (rs, n) -> fromRow(rs), kind.name());
    }

    @Override
    protected Map<String, Object> toRow(VaultEntry e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", e.getId());
        row.put("title", e.getTitle());
        row.put("kind", e.getKind().name());
        row.put("use", e.getUse().name());
        row.put("date", e.getDate());
        row.put("tags", String.join(",", e.getTags()));
        row.put("body", e.getBody());
        return row;
    }

    @Override
    protected VaultEntry fromRow(ResultSet rs) throws SQLException {
        String tagText = rs.getString("tags");
        Set<String> tags = tagText == null || tagText.isBlank()
                ? Set.of()
                : new LinkedHashSet<>(Arrays.asList(tagText.split(",")));
        return new VaultEntry(rs.getString("id"), rs.getString("title"),
                EntryKind.valueOf(rs.getString("kind")), EntryUse.valueOf(rs.getString("use")),
                rs.getString("date"), tags, rs.getString("body"));
    }
}
