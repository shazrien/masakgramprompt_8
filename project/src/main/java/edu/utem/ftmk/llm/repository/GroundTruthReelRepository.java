package edu.utem.ftmk.llm.repository;

import edu.utem.ftmk.llm.entity.GroundTruthReel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroundTruthReelRepository extends JpaRepository<GroundTruthReel, Integer> {
}