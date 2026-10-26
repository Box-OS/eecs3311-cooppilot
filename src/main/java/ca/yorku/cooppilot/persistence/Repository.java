package ca.yorku.cooppilot.persistence;

import java.util.List;
import java.util.Optional;

/** Storage abstraction: services depend on this, never on SQLite directly. */
public interface Repository<T> {

    Optional<T> findById(String id);

    List<T> findAll();

    /** Inserts or replaces the item with the same id. */
    void save(T item);

    void delete(String id);
}
