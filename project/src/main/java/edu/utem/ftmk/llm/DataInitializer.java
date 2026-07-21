package edu.utem.ftmk.llm;

import edu.utem.ftmk.llm.entity.Transcript;
import edu.utem.ftmk.llm.repository.TranscriptRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final TranscriptRepository transcriptRepository;

    // Constructor injection
    public DataInitializer(TranscriptRepository transcriptRepository) {
        this.transcriptRepository = transcriptRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("DataInitializer: Syncing files from classpath...");

        // This looks directly inside src/main/resources/transcripts
        ClassPathResource dir = new ClassPathResource("transcripts/");
        File folder = dir.getFile();

        if (folder.exists() && folder.isDirectory()) {
            for (File file : folder.listFiles()) {
                if (file.isFile()) {
                    // Find matching metadata in your database
                    List<Transcript> matches = transcriptRepository.findByFileName(file.getName());

                    if (!matches.isEmpty()) {
                        String content = Files.readString(file.toPath());
                        System.out.println("Processing: " + file.getName());
                        
                        // Now pass 'content' to your LLMService
                        // llmService.prompt("llama3.1", content);
                    }
                }
            }
        }
    }
}