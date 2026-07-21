package edu.utem.ftmk.llm.repository;

import edu.utem.ftmk.llm.entity.NutritionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface NutritionResultRepository extends JpaRepository<NutritionResult, Integer> {
    Optional<NutritionResult> findByExperimentExperimentId(Integer experimentId);
}