package edu.utem.ftmk.llm.repository;

import edu.utem.ftmk.llm.entity.IngredientResult;
import edu.utem.ftmk.llm.entity.Experiment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface IngredientResultRepository extends JpaRepository<IngredientResult, Integer> {
    
    // ADD THIS LINE:
    List<IngredientResult> findByNutritionResult_Experiment(Experiment experiment);
}