package edu.utem.ftmk.llm.repository;

import edu.utem.ftmk.llm.entity.HumanEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HumanEvaluationRepository extends JpaRepository<HumanEvaluation, Integer> {

    /** Find the evaluation for a specific experiment (one-to-one in practice). */
    Optional<HumanEvaluation> findByExperiment_ExperimentId(Integer experimentId);

    /** All evaluations assigned to a given evaluator. */
    List<HumanEvaluation> findByEvaluatorMatric(String evaluatorMatric);

    /**
     * Full join with experiment → transcript → reel + model + technique.
     * Used by generateHumanEvaluationCsv() to produce the LAYER 4 export.
     */
    @Query("SELECT he FROM HumanEvaluation he " +
           "JOIN FETCH he.experiment e " +
           "JOIN FETCH e.transcript t " +
           "JOIN FETCH t.reel r " +
           "JOIN FETCH e.llmModel m " +
           "JOIN FETCH e.promptTechnique pt " +
           "ORDER BY he.evalId ASC")
    List<HumanEvaluation> findAllWithDetails();
}
