package ca.yorku.cooppilot.persistence;

import ca.yorku.cooppilot.domain.EntryKind;
import ca.yorku.cooppilot.domain.VaultEntry;

import java.util.List;

public interface VaultRepository extends Repository<VaultEntry> {

    List<VaultEntry> findByKind(EntryKind kind);
}
