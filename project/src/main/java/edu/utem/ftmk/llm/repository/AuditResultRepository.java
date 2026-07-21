package edu.utem.ftmk.llm.repository;

import edu.utem.ftmk.llm.entity.AuditResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditResultRepository extends JpaRepository<AuditResult, Integer> {

    /**
     * Returns all cached audit rows for a given experiment.
     * An empty list means the audit has never been computed — run it now and save.
     */
    List<AuditResult> findByExperimentId(Integer experimentId);

    /**
     * Used to wipe cached rows before re-computing (e.g. after a manual force-refresh).
     */
    void deleteByExperimentId(Integer experimentId);
}
