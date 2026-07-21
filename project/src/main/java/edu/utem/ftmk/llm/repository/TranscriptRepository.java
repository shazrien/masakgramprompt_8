package edu.utem.ftmk.llm.repository;

import edu.utem.ftmk.llm.entity.Transcript;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional; // 1. ADD THIS IMPORT

@Repository
public interface TranscriptRepository extends JpaRepository<Transcript, Integer> {
    
    // 2. Spring Data JPA will automatically implement this query
    // Make sure your Transcript entity has a 'reel' field and that field has a 'reelId'
    Optional<Transcript> findByReel_ReelId(Integer reelId);
    
    List<Transcript> findByFileName(String fileName);
}