package edu.utem.ftmk.llm;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
public class LLMService {

    private static final String OLLAMA_BASE_URL = "http://localhost:11434";

    public static final String LLAMA = "llama3.2:3b";
    public static final String PHI = "phi4-mini"; 
    public static final String QWEN = "qwen2.5:3b";
    public static final String SEALION = "aisingapore/Gemma-SEA-LION-v4-4B-VL"; 
    public static final String MEDGEMMA = "medgemma:4b";;

    public ChatModel buildModel(String modelName) {
        return OllamaChatModel.builder()
                .baseUrl(OLLAMA_BASE_URL)
                .modelName(modelName)
                .timeout(Duration.ofMinutes(5))
                .build();
    }

    public String prompt(String modelName, String userPrompt) {
        ChatModel model = buildModel(modelName);
        return model.chat(userPrompt);
    }

    /**
     * Variant used for short, deterministic classification/judging calls (e.g. the
     * Semantic Jury) rather than open-ended extraction. Small local models like
     * llama3.2:3b are prone to adding greetings, explanations, and extra
     * unrequested examples when sampled at default settings - that's what was
     * causing the rambling, inconsistently-formatted output. Temperature 0 makes
     * the model pick its single most likely token every time (deterministic,
     * least "creative" output), and a small output cap gives it less room to
     * wander into commentary after the answer.
     *
     * NOTE: if your installed langchain4j-ollama version doesn't expose
     * .temperature(...) / .numPredict(...) on the builder, just delete the
     * offending line(s) below - the rest of the method still works.
     */
    public String promptStrict(String modelName, String userPrompt) {
        ChatModel model = OllamaChatModel.builder()
                .baseUrl(OLLAMA_BASE_URL)
                .modelName(modelName)
                .timeout(Duration.ofMinutes(5))
                .temperature(0.0)
                .numPredict(150)
                .build();
        return model.chat(userPrompt);
    }
}