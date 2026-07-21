package edu.utem.ftmk.llm.repository;

import edu.utem.ftmk.llm.entity.Reel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReelRepository extends JpaRepository<Reel, Integer> {
}