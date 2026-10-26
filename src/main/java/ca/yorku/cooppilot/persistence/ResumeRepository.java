package ca.yorku.cooppilot.persistence;

import ca.yorku.cooppilot.domain.Resume;

import java.util.Optional;

public interface ResumeRepository extends Repository<Resume> {

    Optional<Resume> findByName(String name);
}
