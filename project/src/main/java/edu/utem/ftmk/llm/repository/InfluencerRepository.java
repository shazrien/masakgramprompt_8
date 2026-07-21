package edu.utem.ftmk.llm.repository;

import edu.utem.ftmk.llm.entity.Influencer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InfluencerRepository extends JpaRepository<Influencer, Integer> {
}