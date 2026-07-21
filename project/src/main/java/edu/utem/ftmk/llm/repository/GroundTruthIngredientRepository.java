package edu.utem.ftmk.llm.repository;

import edu.utem.ftmk.llm.entity.GroundTruthIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface GroundTruthIngredientRepository extends JpaRepository<GroundTruthIngredient, Integer> {
    
    // ADD THIS LINE:
    // This assumes GroundTruthReel has a field named gtReelId
    List<GroundTruthIngredient> findByGroundTruthReel_GtReelId(Integer gtReelId);
}