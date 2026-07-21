package edu.utem.ftmk.llm.repository;

import edu.utem.ftmk.llm.entity.AudioFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AudioFileRepository extends JpaRepository<AudioFile, Integer> {
}