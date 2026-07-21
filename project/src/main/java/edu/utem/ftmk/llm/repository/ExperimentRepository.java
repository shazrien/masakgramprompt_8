package edu.utem.ftmk.llm.repository;

import edu.utem.ftmk.llm.entity.Experiment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExperimentRepository extends JpaRepository<Experiment, Integer> {
}