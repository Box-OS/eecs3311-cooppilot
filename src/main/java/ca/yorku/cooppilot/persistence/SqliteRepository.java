package ca.yorku.cooppilot.persistence;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Shared SQLite CRUD. Subclasses only say how one item maps to a row and back
 * ({@link #toRow} / {@link #fromRow}); the SQL lives here once.
 */
public abstract class SqliteRepository<T> implements Repository<T> {

    protected final JdbcTemplate jdbc;
    private final String table;

    protected SqliteRepository(JdbcTemplate jdbc, String table) {
        this.jdbc = jdbc;
        this.table = table;
    }

    /** Column name -> value, including "id". Insertion order is the column order. */
    protected abstract Map<String, Object> toRow(T item);

    protected abstract T fromRow(ResultSet rs) throws SQLException;

    @Override
    public Optional<T> findById(String id) {
        List<T> found = jdbc.query("SELECT * FROM " + table + " WHERE id = ?", (rs, n) -> fromRow(rs), id);
        return found.stream().findFirst();
    }

    @Override
    public List<T> findAll() {
        return jdbc.query("SELECT * FROM " + table + " ORDER BY rowid", (rs, n) -> fromRow(rs));
    }

    @Override
    public void save(T item) {
        Map<String, Object> row = new LinkedHashMap<>(toRow(item));
        if (!row.containsKey("id")) throw new IllegalStateException("toRow must include an id column");
        String cols = String.join(", ", row.keySet());
        String marks = row.keySet().stream().map(c -> "?").collect(Collectors.joining(", "));
        jdbc.update("INSERT OR REPLACE INTO " + table + " (" + cols + ") VALUES (" + marks + ")",
                new ArrayList<>(row.values()).toArray());
    }

    @Override
    public void delete(String id) {
        jdbc.update("DELETE FROM " + table + " WHERE id = ?", id);
    }

    public int count() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }
}
