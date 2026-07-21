package edu.utem.ftmk.llm.repository;

import edu.utem.ftmk.llm.entity.PromptTechnique;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PromptTechniqueRepository extends JpaRepository<PromptTechnique, Integer> {
}