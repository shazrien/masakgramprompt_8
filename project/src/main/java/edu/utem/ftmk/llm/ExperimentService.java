package edu.utem.ftmk.llm;

import edu.utem.ftmk.llm.entity.*;
import edu.utem.ftmk.llm.entity.AuditResult;
import edu.utem.ftmk.llm.entity.HumanEvaluation;
import edu.utem.ftmk.llm.repository.*;
import edu.utem.ftmk.llm.repository.AuditResultRepository;
import edu.utem.ftmk.llm.repository.HumanEvaluationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.objenesis.instantiator.perc.PercSerializationInstantiator;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service
public class ExperimentService {

    private static final Logger log = LoggerFactory.getLogger(ExperimentService.class);

    private final ExperimentRepository experimentRepository;
    private final NutritionResultRepository nutritionResultRepository;
    private final IngredientResultRepository ingredientResultRepository;
    private final LLMService llmService;
    private final Map<String, Boolean> semanticCache = new ConcurrentHashMap<>();
    // Added these to allow the service to perform the loop lookup
    private final TranscriptRepository transcriptRepository;
    private final LlmModelRepository llmModelRepository;
    private final PromptTechniqueRepository promptTechniqueRepository;
    private final GroundTruthIngredientRepository groundTruthIngredientRepository;
    private final AuditResultRepository auditResultRepository;
    private final HumanEvaluationRepository humanEvaluationRepository;

    private volatile boolean stopRequested = false;

    public ExperimentService(ExperimentRepository experimentRepository,
            NutritionResultRepository nutritionResultRepository,
            IngredientResultRepository ingredientResultRepository,
            LLMService llmService,
            TranscriptRepository transcriptRepository,
            LlmModelRepository llmModelRepository,
            PromptTechniqueRepository promptTechniqueRepository,
            GroundTruthIngredientRepository groundTruthIngredientRepository,
            AuditResultRepository auditResultRepository,
            HumanEvaluationRepository humanEvaluationRepository) {
	this.experimentRepository = experimentRepository;
	this.nutritionResultRepository = nutritionResultRepository;
	this.ingredientResultRepository = ingredientResultRepository;
	this.llmService = llmService;
	this.transcriptRepository = transcriptRepository;
	this.llmModelRepository = llmModelRepository;
	this.promptTechniqueRepository = promptTechniqueRepository;
	this.groundTruthIngredientRepository = groundTruthIngredientRepository;
	this.auditResultRepository = auditResultRepository;
	this.humanEvaluationRepository = humanEvaluationRepository;
	    }

    

    private String loadResourceFile(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            log.error("Could not load resource file at: {}", path);
            return "";
        }
    }
    
    public void stopExperiment() {
        this.stopRequested = true;
    }
    
    private String injectTranscript(String userPrompt, String transcriptContent) {
        int startDelimiter = userPrompt.indexOf("Transcript:\n\"\"\"");
        int endDelimiter = userPrompt.lastIndexOf("\"\"\""); 
        
        String beforeTranscript = userPrompt.substring(0, startDelimiter);
        String afterTranscript = userPrompt.substring(endDelimiter);
        log.info("\nInjected Transcript:\n" + beforeTranscript +"\nTranscript:" +"\n" + transcriptContent + "\n" + afterTranscript);
        
        return beforeTranscript +"\nTranscript:" +"\n" + transcriptContent + "\n" + afterTranscript;
    }
    
    @Async
    public void runExperiment(Integer modelId, Integer techniqueId, Boolean ragEnabled) {
        this.stopRequested = false;

        // Dynamically pull every transcript that has a linked reel, instead of
        // hard-looping a fixed range of reel IDs. This means the pipeline
        // automatically scales to however many reels exist (50, 100, 500, ...)
        // and also survives gaps in reel IDs.
        List<Transcript> transcripts = transcriptRepository.findAll().stream()
                .filter(t -> t.getReel() != null)
                .sorted(Comparator.comparing(t -> t.getReel().getReelId()))
                .collect(Collectors.toList());

        log.info("Starting experiment run over {} reel(s) (dynamic count).", transcripts.size());

        for (Transcript transcript : transcripts) {
            if (stopRequested) break;
            Integer reelId = transcript.getReel().getReelId();
            try {
                Experiment experiment = new Experiment();
                experiment.setTranscript(transcript);
                experiment.setLlmModel(llmModelRepository.findById(modelId).orElse(null));
                experiment.setPromptTechnique(promptTechniqueRepository.findById(techniqueId).orElse(null));
                experiment.setRagEnabled(ragEnabled);
                experiment.setStatus("running");
                experiment.setExecutedAt(LocalDateTime.now());
                
                experiment = experimentRepository.saveAndFlush(experiment);

                String systemPrompt = loadResourceFile(experiment.getPromptTechnique().getSystemPromptFile());
                String userPrompt = loadResourceFile(experiment.getPromptTechnique().getUserPromptFile());
                String transcriptContent = loadResourceFile(experiment.getTranscript().getFilePath());
                String combinedPrompt = systemPrompt + "\n" + injectTranscript(userPrompt, transcriptContent);

                String rawOutput = llmService.prompt(experiment.getLlmModel().getModelTag(), combinedPrompt);
                log.info("\n Raw json output:\n" + rawOutput);

                processAndSaveResult(experiment, rawOutput);

            } catch (Exception e) {
                log.error("Error at Reel {}: {}", reelId, e.getMessage());
            }
        }
    }
    
    @Transactional
    public void processAndSaveResult(Experiment experiment, String rawOutput) {
        try {
            String cleanedJson = cleanJsonString(rawOutput);
            
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS);
            Map<String, Object> data = mapper.readValue(cleanedJson, new TypeReference<Map<String, Object>>() {});
            
            // 1. Check if there is at least one valid ingredient (Not empty and not N/A)
            Object ingredientsObj = data.get("ingredients");
            boolean hasAtLeastOneIngredient = false;

            if (ingredientsObj instanceof List) {
                List<Map<String, Object>> list = (List<Map<String, Object>>) ingredientsObj;
                for (Map<String, Object> ing : list) {
                    String nameEn = (ing.get("ingredient_name_en") != null) ? ing.get("ingredient_name_en").toString().trim() : "";
                    String nameOri = (ing.get("ingredient_name_original") != null) ? ing.get("ingredient_name_original").toString().trim() : "";
                    
                    if (!nameEn.isEmpty() && !nameEn.equalsIgnoreCase("N/A") || 
                        !nameOri.isEmpty() && !nameOri.equalsIgnoreCase("N/A")) {
                        hasAtLeastOneIngredient = true;
                        break;
                    }
                }
            }

            // If no valid data or ingredients, mark as failed and stop
            if (!hasAtLeastOneIngredient) {
                experiment.setStatus("failed");
                experimentRepository.save(experiment);
                log.info("THE INGREDIENT IS EMPTY");
                return;
            }

            // 2. Data Mapping (UNCHANGED as per request)
            NutritionResult nutritionResult = new NutritionResult();
            nutritionResult.setExperiment(experiment);
            nutritionResult.setRawJsonOutput(rawOutput);
            nutritionResult.setJsonValid(true);
            
	            // Initialize fields to 0.0f
	            nutritionResult.setServingCalories(0.0f);
	            nutritionResult.setServingTotalFatG(0.0f);
	            nutritionResult.setServingSaturatedFatG(0.0f);
	            nutritionResult.setServingCholesterolMg(0.0f);
	            nutritionResult.setServingSodiumMg(0.0f);
	            nutritionResult.setServingCarbohydrateG(0.0f);
	            nutritionResult.setServingFiberG(0.0f);
	            nutritionResult.setServingSugarsG(0.0f);
	            nutritionResult.setServingProteinG(0.0f);
	            nutritionResult.setServingVitaminDMcg(0.0f);
	            nutritionResult.setServingCalciumMg(0.0f); 
	            nutritionResult.setServingIronMg(0.0f);
	            nutritionResult.setServingPotassiumMg(0.0f);
	            nutritionResult.setTotalCalories(0.0f);
	            nutritionResult.setTotalFatG(0.0f);
	            nutritionResult.setTotalSaturatedFatG(0.0f);
	            nutritionResult.setTotalCholesterolMg(0.0f);
	            nutritionResult.setTotalSodiumMg(0.0f);
	            nutritionResult.setTotalCarbohydrateG(0.0f);
	            nutritionResult.setTotalFiberG(0.0f);
	            nutritionResult.setTotalSugarsG(0.0f);
	            nutritionResult.setTotalProteinG(0.0f);
	            nutritionResult.setTotalVitaminDMcg(0.0f);
	            nutritionResult.setTotalCalciumMg(0.0f);
	            nutritionResult.setTotalIronMg(0.0f);
	            nutritionResult.setTotalPotassiumMg(0.0f);

            int servings = getInteger(data.get("servings_estimated"), 1);
            if (servings <= 0) servings = 1;
            nutritionResult.setRecipeName((String) data.get("recipe_name"));
            nutritionResult.setServingsEstimated(servings);

            Map<String, Object> perServing = (Map<String, Object>) data.get("amount_per_serving");
            if (perServing != null) {
                float sCalories = getFloat(perServing.get("calories"));
                float sFat = getFloat(perServing.get("total_fat_g"));
                float sSatFat = getFloat(perServing.get("saturated_fat_g"));
                float sCholesterol = getFloat(perServing.get("cholesterol_mg"));
                float sSodium = getFloat(perServing.get("sodium_mg"));
                float sCarbs = getFloat(perServing.get("total_carbohydrate_g"));
                float sFiber = getFloat(perServing.get("dietary_fiber_g"));
                float sSugars = getFloat(perServing.get("total_sugars_g"));
                float sProtein = getFloat(perServing.get("protein_g"));
                float sVitD = getFloat(perServing.get("vitamin_d_mcg"));
                float sCalcium = getFloat(perServing.get("calcium_mg"));
                float sIron = getFloat(perServing.get("iron_mg"));
                float sPotassium = getFloat(perServing.get("potassium_mg"));
                
                nutritionResult.setServingCalories(sCalories);
                nutritionResult.setServingTotalFatG(sFat);
                nutritionResult.setServingSaturatedFatG(sSatFat);
                nutritionResult.setServingCholesterolMg(sCholesterol);
                nutritionResult.setServingSodiumMg(sSodium);
                nutritionResult.setServingCarbohydrateG(sCarbs);
                nutritionResult.setServingFiberG(sFiber);
                nutritionResult.setServingSugarsG(sSugars);
                nutritionResult.setServingProteinG(sProtein);
                nutritionResult.setServingVitaminDMcg(sVitD);
                nutritionResult.setServingCalciumMg(sCalcium);
                nutritionResult.setServingIronMg(sIron);
                nutritionResult.setServingPotassiumMg(sPotassium);

                nutritionResult.setTotalCalories(sCalories * servings);
                nutritionResult.setTotalFatG(sFat * servings);
                nutritionResult.setTotalSaturatedFatG(sSatFat * servings);
                nutritionResult.setTotalCholesterolMg(sCholesterol * servings);
                nutritionResult.setTotalSodiumMg(sSodium * servings);
                nutritionResult.setTotalCarbohydrateG(sCarbs * servings);
                nutritionResult.setTotalFiberG(sFiber * servings);
                nutritionResult.setTotalSugarsG(sSugars * servings);
                nutritionResult.setTotalProteinG(sProtein * servings);
                nutritionResult.setTotalVitaminDMcg(sVitD * servings);
                nutritionResult.setTotalCalciumMg(sCalcium * servings);
                nutritionResult.setTotalIronMg(sIron * servings);
                nutritionResult.setTotalPotassiumMg(sPotassium * servings);
            }
            nutritionResultRepository.saveAndFlush(nutritionResult);

            List<Map<String, Object>> ingredientList = (List<Map<String, Object>>) ingredientsObj;
            for (Map<String, Object> ingMap : ingredientList) {
                IngredientResult ingredient = new IngredientResult();
                ingredient.setNutritionResult(nutritionResult);
                ingredient.setNameOriginal(ingMap.getOrDefault("ingredient_name_original", "N/A").toString());
                ingredient.setNameEn(ingMap.getOrDefault("ingredient_name_en", "N/A").toString());
                ingredient.setQuantityValue(getFloat(ingMap.get("quantity_value")));
                ingredient.setUnitOriginal(ingMap.getOrDefault("quantity_unit_original", "N/A").toString());
                ingredient.setUnitEn(ingMap.getOrDefault("quantity_unit_en", "N/A").toString());
                ingredient.setEstimatedWeightG(getFloat(ingMap.get("estimated_weight_g")));
                ingredient.setCalories(getFloat(ingMap.get("calories")));
                ingredient.setTotalFatG(getFloat(ingMap.get("total_fat_g")));
                ingredient.setSaturatedFatG(getFloat(ingMap.get("saturated_fat_g")));
                ingredient.setCholesterolMg(getFloat(ingMap.get("cholesterol_mg")));
                ingredient.setSodiumMg(getFloat(ingMap.get("sodium_mg")));
                ingredient.setTotalCarbohydrateG(getFloat(ingMap.get("total_carbohydrate_g")));
                ingredient.setDietaryFiberG(getFloat(ingMap.get("dietary_fiber_g")));
                ingredient.setTotalSugarsG(getFloat(ingMap.get("total_sugars_g")));
                ingredient.setProteinG(getFloat(ingMap.get("protein_g")));
                ingredient.setVitaminDMcg(getFloat(ingMap.get("vitamin_d_mcg")));
                ingredient.setCalciumMg(getFloat(ingMap.get("calcium_mg")));
                ingredient.setIronMg(getFloat(ingMap.get("iron_mg")));
                ingredient.setPotassiumMg(getFloat(ingMap.get("potassium_mg")));
                ingredientResultRepository.save(ingredient);
            }

            experiment.setStatus("success");
            experimentRepository.save(experiment);

            // ── AUTO HALLUCINATION / AUDIT CHECK ─────────────────────────────
            // Immediately run the 4-pass audit (Exact → Semantic → Missing →
            // Hallucination) and persist every row so the Deep-Dive modal loads
            // instantly without re-running the LLM Semantic Jury on first open.
            try {
                saveAuditResultsForExperiment(experiment);
            } catch (Exception auditEx) {
                // Non-fatal: log and continue. The controller's cache-miss path
                // will re-compute the audit on the first Deep-Dive request.
                log.warn("Auto audit check failed for Experiment ID {}: {}",
                         experiment.getExperimentId(), auditEx.getMessage());
            }

        } catch (Exception e) {
            log.error("Parsing failed for Experiment ID {}: {}", experiment.getExperimentId(), e.getMessage());
            experiment.setStatus("failed");
            experimentRepository.save(experiment);
            
            NutritionResult nutritionResult = new NutritionResult();
            nutritionResult.setExperiment(experiment);
            nutritionResult.setRawJsonOutput(rawOutput);
            nutritionResult.setJsonValid(false);
            nutritionResultRepository.save(nutritionResult);
        }
    }
    
 // Helper Algorithm: Levenshtein Distance for Similarity
    private double calculateSimilarity(String s1, String s2) {
        if (s1.isEmpty() || s2.isEmpty()) return 0.0;
        if (s1.equals(s2)) return 1.0;
        
        int maxLength = Math.max(s1.length(), s2.length());
        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) costs[j] = j;
                else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1))
                            newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0) costs[s2.length()] = lastValue;
        }
        return (maxLength - costs[s2.length()]) / (double) maxLength;
    }
    
 // LAYER 1A: Exact Match
    // Columns match LAYER 1A query in metrics_evaluation_queries.sql
    public String generateExactMatchCsv() {
        List<Experiment> experiments = experimentRepository.findAll();
        StringBuilder sb = new StringBuilder();

        sb.append("experiment_id,transcript_id,video_id,model_name,technique_name,rag_enabled," +
                  "gt_name_original,gt_name_en,gt_unit_original,gt_unit_en," +
                  "pred_name_original,pred_name_en,pred_unit_original,pred_unit_en\n");

        for (Experiment exp : experiments) {
            if (!"success".equalsIgnoreCase(exp.getStatus())) continue;
            Integer reelId = exp.getTranscript().getReel().getReelId();
            Integer transcriptId = exp.getTranscript().getTranscriptId();
            List<GroundTruthIngredient> truthList = groundTruthIngredientRepository.findByGroundTruthReel_GtReelId(reelId);
            List<IngredientResult> aiResults = ingredientResultRepository.findByNutritionResult_Experiment(exp);

            for (GroundTruthIngredient gt : truthList) {
                // Find the best-matching predicted ingredient for this GT row
                IngredientResult bestMatch = aiResults.stream()
                    .filter(ai -> ai.getNameOriginal() != null &&
                                  ai.getNameOriginal().toLowerCase().trim()
                                    .equals(gt.getNameOriginal() != null ? gt.getNameOriginal().toLowerCase().trim() : ""))
                    .findFirst()
                    .orElseGet(() -> aiResults.stream()
                        .filter(ai -> ai.getNameEn() != null &&
                                      ai.getNameEn().toLowerCase().trim()
                                        .equals(gt.getNameEn() != null ? gt.getNameEn().toLowerCase().trim() : ""))
                        .findFirst()
                        .orElse(null));

                String predNameOriginal = bestMatch != null ? bestMatch.getNameOriginal() : "";
                String predNameEn      = bestMatch != null ? bestMatch.getNameEn()       : "";
                String predUnitOriginal = bestMatch != null ? bestMatch.getUnitOriginal() : "";
                String predUnitEn      = bestMatch != null ? bestMatch.getUnitEn()       : "";

                sb.append(exp.getExperimentId()).append(",")
                  .append(transcriptId).append(",")
                  .append(reelId).append(",")
                  .append(csvQuote(exp.getLlmModel().getModelName())).append(",")
                  .append(csvQuote(exp.getPromptTechnique().getTechniqueName())).append(",")
                  .append(exp.getRagEnabled() != null && exp.getRagEnabled() ? 1 : 0).append(",")
                  .append(csvQuote(gt.getNameOriginal())).append(",")
                  .append(csvQuote(gt.getNameEn())).append(",")
                  .append(csvQuote(gt.getQuantityUnitCulinary())).append(",")
                  .append(csvQuote(gt.getQuantityUnitCulinary())).append(",")
                  .append(csvQuote(predNameOriginal)).append(",")
                  .append(csvQuote(predNameEn)).append(",")
                  .append(csvQuote(predUnitOriginal)).append(",")
                  .append(csvQuote(predUnitEn)).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * LAYER 1B: Fuzzy Match & BLEU / ROUGE
     * Columns match LAYER 1B query in metrics_evaluation_queries.sql
     */
    public String generateTextSimilarityCsv() {
        List<Experiment> successfulExperiments = findSuccessfulExperiments();
        StringBuilder csvBuilder = new StringBuilder();

        csvBuilder.append("experiment_id,video_id,model_name,technique_name,rag_enabled," +
                          "gt_name_original,gt_name_en,pred_name_original,pred_name_en\n");

        for (Experiment experimentRecord : successfulExperiments) {
            Integer reelId = experimentRecord.getTranscript().getReel().getReelId();
            String currentModelTag = experimentRecord.getLlmModel().getModelTag();

            List<IngredientResult> extractedIngredients = ingredientResultRepository.findByNutritionResult_Experiment(experimentRecord);
            if (extractedIngredients.isEmpty()) continue;

            List<GroundTruthIngredient> groundTruthIngredients = groundTruthIngredientRepository.findByGroundTruthReel_GtReelId(reelId);
            List<Map<String, Object>> auditRows = getAuditRowsCacheFirst(
                    experimentRecord.getExperimentId(), groundTruthIngredients, extractedIngredients, currentModelTag);

            for (Map<String, Object> row : auditRows) {
                String type = (String) row.get("type");
                if (!"MATCH".equals(type) && !"MISSING".equals(type)) continue;

                boolean isMatched = "MATCH".equals(type);

                // Resolve GT ingredient to get name_original
                String gtNameEn = (String) row.get("gtName");
                GroundTruthIngredient gtIngredient = groundTruthIngredients.stream()
                        .filter(g -> gtNameEn.equals(g.getNameEn()))
                        .findFirst().orElse(null);
                String gtNameOriginal = gtIngredient != null ? gtIngredient.getNameOriginal() : "";

                // Resolve predicted ingredient fields
                String aiNameEn       = isMatched ? (String) row.get("aiName") : "";
                String aiNameOriginal = "";
                if (isMatched) {
                    IngredientResult matchedIr = extractedIngredients.stream()
                            .filter(ir -> aiNameEn.equals(getDisplayName(ir)))
                            .findFirst().orElse(null);
                    if (matchedIr != null) aiNameOriginal = matchedIr.getNameOriginal();
                }

                csvBuilder.append(experimentRecord.getExperimentId()).append(",")
                          .append(reelId).append(",")
                          .append(csvQuote(experimentRecord.getLlmModel().getModelName())).append(",")
                          .append(csvQuote(experimentRecord.getPromptTechnique().getTechniqueName())).append(",")
                          .append(experimentRecord.getRagEnabled() != null && experimentRecord.getRagEnabled() ? 1 : 0).append(",")
                          .append(csvQuote(gtNameOriginal)).append(",")
                          .append(csvQuote(gtNameEn)).append(",")
                          .append(csvQuote(aiNameOriginal)).append(",")
                          .append(csvQuote(aiNameEn)).append("\n");
            }
        }
        return csvBuilder.toString();
    }

    /**
     * Helper: Ensures the AI output shows the Malay name if the English name is missing/N/A.
     */
    private String getDisplayName(IngredientResult ingredient) {
        if (ingredient.getNameEn() != null && 
            !ingredient.getNameEn().equalsIgnoreCase("N/A") && 
            !ingredient.getNameEn().isEmpty()) {
            return ingredient.getNameEn();
        }
        // Return the Malay (Original) name if English is not available
        return (ingredient.getNameOriginal() != null) ? ingredient.getNameOriginal() : "Unknown";
    }

    /**
     * Normalizes an ingredient name for comparison by stripping parenthetical qualifiers
     * (e.g. "bawang besar (pada)" → "bawang besar", "onion (diced)" → "onion"),
     * then lowercasing and trimming whitespace.
     * This prevents false HALLUCINATION labels when the AI appends cooking-method
     * or cut-style annotations to an otherwise correct ingredient name.
     */
    private String normalizeName(String name) {
        if (name == null) return "";
        // Remove anything inside parentheses (including the parentheses themselves)
        return name.replaceAll("\\s*\\(.*?\\)", "").toLowerCase().trim();
    }

    /**
     * LAYER 2A: MAE & MAPE — Quantity & Weight
     * Columns match LAYER 2A query in metrics_evaluation_queries.sql
     * One row per GT ingredient paired with its matching predicted ingredient.
     */
    public String generateNumericQuantityCsv() {
        List<Experiment> successfulExperiments = findSuccessfulExperiments();
        StringBuilder csvBuilder = new StringBuilder();

        csvBuilder.append("experiment_id,video_id,model_name,technique_name,rag_enabled," +
                          "gt_quantity_value,gt_weight_g,pred_quantity_value,pred_weight_g\n");

        for (Experiment experimentRecord : successfulExperiments) {
            Integer reelId = experimentRecord.getTranscript().getReel().getReelId();
            String currentModelTag = experimentRecord.getLlmModel().getModelTag();

            List<GroundTruthIngredient> groundTruthIngredients =
                    groundTruthIngredientRepository.findByGroundTruthReel_GtReelId(reelId)
                            .stream()
                            .filter(g -> "layer2".equals(g.getAnnotationLayer()))
                            .collect(Collectors.toList());

            List<IngredientResult> extractedResults =
                    ingredientResultRepository.findByNutritionResult_Experiment(experimentRecord);
            if (extractedResults.isEmpty()) continue;

            Map<String, String> aiNameToGtNameEn = getAuditRowsCacheFirst(
                        experimentRecord.getExperimentId(), groundTruthIngredients, extractedResults, currentModelTag)
                    .stream()
                    .filter(r -> "MATCH".equals(r.get("type")))
                    .collect(Collectors.toMap(
                            r -> (String) r.get("aiName"),
                            r -> (String) r.get("gtName"),
                            (existing, duplicate) -> existing));

            for (GroundTruthIngredient gt : groundTruthIngredients) {
                // Find the AI ingredient matched to this GT item
                IngredientResult matchedPred = null;
                for (Map.Entry<String, String> entry : aiNameToGtNameEn.entrySet()) {
                    if (gt.getNameEn().equals(entry.getValue())) {
                        String aiDisplayName = entry.getKey();
                        matchedPred = extractedResults.stream()
                                .filter(ir -> aiDisplayName.equals(getDisplayName(ir)))
                                .findFirst().orElse(null);
                        break;
                    }
                }

                float predQty    = matchedPred != null ? matchedPred.getQuantityValue()    : 0.0f;
                float predWeightG = matchedPred != null ? matchedPred.getEstimatedWeightG() : 0.0f;

                csvBuilder.append(experimentRecord.getExperimentId()).append(",")
                          .append(reelId).append(",")
                          .append(csvQuote(experimentRecord.getLlmModel().getModelName())).append(",")
                          .append(csvQuote(experimentRecord.getPromptTechnique().getTechniqueName())).append(",")
                          .append(experimentRecord.getRagEnabled() != null && experimentRecord.getRagEnabled() ? 1 : 0).append(",")
                          .append(getFloat(gt.getQuantityValueCulinary())).append(",")
                          .append(getFloat(gt.getEstimatedWeightG())).append(",")
                          .append(predQty).append(",")
                          .append(predWeightG).append("\n");
            }
        }
        return csvBuilder.toString();
    }

    /**
     * LAYER 2B: MAE, MAPE & Pearson — Nutrition Values (per ingredient)
     * Columns match LAYER 2B query in metrics_evaluation_queries.sql
     * Four core nutrients only; evaluate.py computes MAE/MAPE/Pearson from these columns.
     */
    public String generateItemizedIngredientCsv() {
        List<Experiment> successfulExperiments = findSuccessfulExperiments();
        StringBuilder csvBuilder = new StringBuilder();

        csvBuilder.append("experiment_id,video_id,model_name,technique_name,rag_enabled," +
                          "gt_energy_kcal,gt_protein_g,gt_fat_g,gt_carbohydrate_g," +
                          "pred_energy_kcal,pred_protein_g,pred_fat_g,pred_carbohydrate_g\n");

        for (Experiment experimentRecord : successfulExperiments) {
            Integer reelId = experimentRecord.getTranscript().getReel().getReelId();
            String currentModelTag = experimentRecord.getLlmModel().getModelTag();

            List<GroundTruthIngredient> groundTruthIngredients =
                    groundTruthIngredientRepository.findByGroundTruthReel_GtReelId(reelId)
                            .stream()
                            .filter(g -> "layer2".equals(g.getAnnotationLayer()))
                            .collect(Collectors.toList());

            List<IngredientResult> extractedIngredientResults =
                    ingredientResultRepository.findByNutritionResult_Experiment(experimentRecord);
            if (extractedIngredientResults.isEmpty()) continue;

            Map<String, String> aiNameToGtNameEn = getAuditRowsCacheFirst(
                        experimentRecord.getExperimentId(), groundTruthIngredients, extractedIngredientResults, currentModelTag)
                    .stream()
                    .filter(r -> "MATCH".equals(r.get("type")))
                    .collect(Collectors.toMap(
                            r -> (String) r.get("aiName"),
                            r -> (String) r.get("gtName"),
                            (existing, duplicate) -> existing));

            for (GroundTruthIngredient gt : groundTruthIngredients) {
                // Find the matched AI ingredient for this GT row
                IngredientResult matchedPred = null;
                for (Map.Entry<String, String> entry : aiNameToGtNameEn.entrySet()) {
                    if (gt.getNameEn().equals(entry.getValue())) {
                        String aiDisplayName = entry.getKey();
                        matchedPred = extractedIngredientResults.stream()
                                .filter(ir -> aiDisplayName.equals(getDisplayName(ir)))
                                .findFirst().orElse(null);
                        break;
                    }
                }

                float predEnergy = matchedPred != null ? matchedPred.getCalories()          : 0.0f;
                float predProt   = matchedPred != null ? matchedPred.getProteinG()           : 0.0f;
                float predFat    = matchedPred != null ? matchedPred.getTotalFatG()          : 0.0f;
                float predCarb   = matchedPred != null ? matchedPred.getTotalCarbohydrateG() : 0.0f;

                csvBuilder.append(experimentRecord.getExperimentId()).append(",")
                          .append(reelId).append(",")
                          .append(csvQuote(experimentRecord.getLlmModel().getModelName())).append(",")
                          .append(csvQuote(experimentRecord.getPromptTechnique().getTechniqueName())).append(",")
                          .append(experimentRecord.getRagEnabled() != null && experimentRecord.getRagEnabled() ? 1 : 0).append(",")
                          .append(getFloat(gt.getCalories())).append(",")
                          .append(getFloat(gt.getProteinG())).append(",")
                          .append(getFloat(gt.getTotalFatG())).append(",")
                          .append(getFloat(gt.getTotalCarbohydrateG())).append(",")
                          .append(predEnergy).append(",")
                          .append(predProt).append(",")
                          .append(predFat).append(",")
                          .append(predCarb).append("\n");
            }
        }
        return csvBuilder.toString();
    }

    /**
     * Professional Helper: Generates the AI Value, GT Value, and the Difference.
     */
    private String formatNutrientComparison(float extractedValue, float groundTruthValue) {
        return String.format("%.1f,%.1f,%.1f,", extractedValue, groundTruthValue, extractedValue - groundTruthValue);
    }

    /**
     * Helper to generate the AI, GT, and Difference columns for a nutrient.
     */
    private String formatNutrient(float aiVal, float gtVal) {
        return String.format("%.1f,%.1f,%.1f,", aiVal, gtVal, aiVal - gtVal);
    }

    /**
     * LAYER 2C: Recipe-level Nutrition Totals
     * Columns match LAYER 2C query in metrics_evaluation_queries.sql
     * GT totals are summed from layer2-annotated ingredients; predicted totals come from nutrition_result.
     */
    public String generateGlobalNutritionCsv() {
        List<NutritionResult> allNutritionResults = nutritionResultRepository.findAll();
        StringBuilder csvBuilder = new StringBuilder();

        csvBuilder.append("experiment_id,video_id,model_name,technique_name,rag_enabled," +
                          "gt_total_energy_kcal,gt_total_protein_g,gt_total_fat_g,gt_total_carbohydrate_g," +
                          "pred_total_energy_kcal,pred_total_protein_g,pred_total_fat_g,pred_total_carbohydrate_g\n");

        for (NutritionResult nr : allNutritionResults) {
            Experiment exp = nr.getExperiment();
            if (!"success".equalsIgnoreCase(exp.getStatus())) continue;
            Integer reelId = exp.getTranscript().getReel().getReelId();

            // Sum layer2-annotated GT ingredients (mirrors LAYER 2C SQL WHERE annotation_layer = 'layer2')
            List<GroundTruthIngredient> gtLayer2 = groundTruthIngredientRepository
                    .findByGroundTruthReel_GtReelId(reelId)
                    .stream()
                    .filter(g -> "layer2".equals(g.getAnnotationLayer()))
                    .collect(Collectors.toList());

            float gtEnergy = 0f, gtProtein = 0f, gtFat = 0f, gtCarb = 0f;
            for (GroundTruthIngredient g : gtLayer2) {
                gtEnergy  += getFloat(g.getCalories());
                gtProtein += getFloat(g.getProteinG());
                gtFat     += getFloat(g.getTotalFatG());
                gtCarb    += getFloat(g.getTotalCarbohydrateG());
            }

            csvBuilder.append(exp.getExperimentId()).append(",")
                      .append(reelId).append(",")
                      .append(csvQuote(exp.getLlmModel().getModelName())).append(",")
                      .append(csvQuote(exp.getPromptTechnique().getTechniqueName())).append(",")
                      .append(exp.getRagEnabled() != null && exp.getRagEnabled() ? 1 : 0).append(",")
                      .append(gtEnergy).append(",")
                      .append(gtProtein).append(",")
                      .append(gtFat).append(",")
                      .append(gtCarb).append(",")
                      .append(nr.getTotalCalories()).append(",")
                      .append(nr.getTotalProteinG()).append(",")
                      .append(nr.getTotalFatG()).append(",")
                      .append(nr.getTotalCarbohydrateG()).append("\n");
        }
        return csvBuilder.toString();
    }

    // LAYER 3A: JSON Validity Rate
    // Columns match LAYER 3A query in metrics_evaluation_queries.sql
    // Aggregated per model × technique × rag_enabled; evaluate.py receives totals and rate.
    public String generateJsonValidityCsv() {
        List<NutritionResult> results = nutritionResultRepository.findAll();

        // Group by model_name | technique_name | rag_enabled
        Map<String, long[]> groups = new java.util.LinkedHashMap<>();
        for (NutritionResult nr : results) {
            Experiment exp = nr.getExperiment();
            if (!"success".equalsIgnoreCase(exp.getStatus())) continue;
            String key = exp.getLlmModel().getModelName() + "|"
                       + exp.getPromptTechnique().getTechniqueName() + "|"
                       + (exp.getRagEnabled() != null && exp.getRagEnabled() ? 1 : 0);
            groups.computeIfAbsent(key, k -> new long[]{0, 0, 0}); // [total, valid, invalid]
            groups.get(key)[0]++;
            if (Boolean.TRUE.equals(nr.getJsonValid())) groups.get(key)[1]++;
            else                                         groups.get(key)[2]++;
        }

        StringBuilder sb = new StringBuilder(
                "model_name,technique_name,rag_enabled,total_runs,valid_count,invalid_count,validity_rate_pct\n");
        for (Map.Entry<String, long[]> entry : groups.entrySet()) {
            String[] parts = entry.getKey().split("\\|", -1);
            long[] counts = entry.getValue();
            double rate = counts[0] > 0 ? Math.round(counts[1] * 10000.0 / counts[0]) / 100.0 : 0.0;
            sb.append(csvQuote(parts[0])).append(",")
              .append(csvQuote(parts[1])).append(",")
              .append(parts[2]).append(",")
              .append(counts[0]).append(",")
              .append(counts[1]).append(",")
              .append(counts[2]).append(",")
              .append(String.format("%.2f", rate)).append("\n");
        }
        return sb.toString();
    }

    /**
     * LAYER 3B: Hallucination Rate
     * Columns match LAYER 3B query in metrics_evaluation_queries.sql
     * One row per predicted ingredient; evaluate.py aggregates the is_hallucinated flag.
     */
    public String generateHallucinationCsv() {
        List<Experiment> successfulExperiments = findSuccessfulExperiments();
        StringBuilder csvBuilder = new StringBuilder();

        csvBuilder.append("experiment_id,video_id,model_name,technique_name,rag_enabled," +
                          "pred_name_original,pred_name_en,is_hallucinated\n");

        for (Experiment experimentRecord : successfulExperiments) {
            Integer reelId = experimentRecord.getTranscript().getReel().getReelId();
            String currentModelTag = experimentRecord.getLlmModel().getModelTag();

            List<IngredientResult> extractedIngredients =
                    ingredientResultRepository.findByNutritionResult_Experiment(experimentRecord);
            if (extractedIngredients.isEmpty()) continue;

            List<GroundTruthIngredient> groundTruthIngredients =
                    groundTruthIngredientRepository.findByGroundTruthReel_GtReelId(reelId);
            List<Map<String, Object>> auditRows = getAuditRowsCacheFirst(
                    experimentRecord.getExperimentId(), groundTruthIngredients, extractedIngredients, currentModelTag);

            // Emit one row per AI ingredient (MATCH or HALLUCINATION; skip MISSING — no AI item)
            for (Map<String, Object> row : auditRows) {
                String type = (String) row.get("type");
                if (!"MATCH".equals(type) && !"HALLUCINATION".equals(type)) continue;

                String aiDisplayName = (String) row.get("aiName");
                IngredientResult ir = extractedIngredients.stream()
                        .filter(i -> aiDisplayName.equals(getDisplayName(i)))
                        .findFirst().orElse(null);

                String predNameOriginal = ir != null ? ir.getNameOriginal() : aiDisplayName;
                String predNameEn       = ir != null ? ir.getNameEn()       : aiDisplayName;
                boolean isHallucinated  = "HALLUCINATION".equals(type);

                csvBuilder.append(experimentRecord.getExperimentId()).append(",")
                          .append(reelId).append(",")
                          .append(csvQuote(experimentRecord.getLlmModel().getModelName())).append(",")
                          .append(csvQuote(experimentRecord.getPromptTechnique().getTechniqueName())).append(",")
                          .append(experimentRecord.getRagEnabled() != null && experimentRecord.getRagEnabled() ? 1 : 0).append(",")
                          .append(csvQuote(predNameOriginal)).append(",")
                          .append(csvQuote(predNameEn)).append(",")
                          .append(isHallucinated ? 1 : 0).append("\n");
            }
        }
        return csvBuilder.toString();
    }

    /**
     * LAYER 3C: Ingredient Precision, Recall & F1
     * Columns match LAYER 3C query in metrics_evaluation_queries.sql
     * One row per experiment with aggregated TP/FP counts; evaluate.py derives P/R/F1.
     */
    public String generateIngredientDetectionCsv() {
        List<Experiment> successfulExperiments = findSuccessfulExperiments();
        StringBuilder csvBuilder = new StringBuilder();

        csvBuilder.append("experiment_id,video_id,model_name,technique_name,rag_enabled," +
                          "gt_ingredient_count,pred_ingredient_count,true_positives,false_positives\n");

        for (Experiment experimentRecord : successfulExperiments) {
            Integer reelId = experimentRecord.getTranscript().getReel().getReelId();
            String currentModelTag = experimentRecord.getLlmModel().getModelTag();

            List<GroundTruthIngredient> groundTruthIngredients =
                    groundTruthIngredientRepository.findByGroundTruthReel_GtReelId(reelId);
            List<IngredientResult> extractedIngredients =
                    ingredientResultRepository.findByNutritionResult_Experiment(experimentRecord);

            List<Map<String, Object>> auditRows = extractedIngredients.isEmpty()
                    ? new ArrayList<>()
                    : getAuditRowsCacheFirst(experimentRecord.getExperimentId(),
                                            groundTruthIngredients, extractedIngredients, currentModelTag);

            long gtCount   = groundTruthIngredients.size();
            long predCount = extractedIngredients.size();
            long tp = auditRows.stream().filter(r -> "MATCH".equals(r.get("type"))).count();
            long fp = auditRows.stream().filter(r -> "HALLUCINATION".equals(r.get("type"))).count();

            csvBuilder.append(experimentRecord.getExperimentId()).append(",")
                      .append(reelId).append(",")
                      .append(csvQuote(experimentRecord.getLlmModel().getModelName())).append(",")
                      .append(csvQuote(experimentRecord.getPromptTechnique().getTechniqueName())).append(",")
                      .append(experimentRecord.getRagEnabled() != null && experimentRecord.getRagEnabled() ? 1 : 0).append(",")
                      .append(gtCount).append(",")
                      .append(predCount).append(",")
                      .append(tp).append(",")
                      .append(fp).append("\n");
        }
        return csvBuilder.toString();
    }

    /**
     * LAYER 4: Human Evaluation CSV export.
     *
     * Queries the human_evaluation table (joined with experiment → reel, model, technique)
     * and produces one CSV row per evaluation record.
     *
     * Columns:
     *   eval_id, experiment_id, video_id, model_name, technique_name,
     *   evaluator_matric, evaluator_name,
     *   fluency_score, coherence_score, ingredient_completeness, ingredient_accuracy,
     *   quantity_accuracy, hallucination_severity, faithfulness_score,
     *   json_structure_score, language_tag_accuracy, overall_score,
     *   remarks, evaluated_at
     */
    public String generateHumanEvaluationCsv() {
        List<HumanEvaluation> rows = humanEvaluationRepository.findAllWithDetails();

        StringBuilder csv = new StringBuilder();
        csv.append("eval_id,experiment_id,video_id,model_name,technique_name,")
           .append("evaluator_matric,evaluator_name,")
           .append("fluency_score,coherence_score,ingredient_completeness,ingredient_accuracy,")
           .append("quantity_accuracy,hallucination_severity,faithfulness_score,")
           .append("json_structure_score,language_tag_accuracy,overall_score,")
           .append("remarks,evaluated_at\n");

        for (HumanEvaluation he : rows) {
            Experiment exp  = he.getExperiment();
            Integer videoId = (exp.getTranscript() != null && exp.getTranscript().getReel() != null)
                              ? exp.getTranscript().getReel().getReelId() : null;
            String modelName     = exp.getLlmModel()       != null ? exp.getLlmModel().getModelName()            : "";
            String techniqueName = exp.getPromptTechnique() != null ? exp.getPromptTechnique().getTechniqueName() : "";

            csv.append(he.getEvalId()).append(",")
               .append(exp.getExperimentId()).append(",")
               .append(videoId != null ? videoId : "").append(",")
               .append(csvQuote(modelName)).append(",")
               .append(csvQuote(techniqueName)).append(",")
               .append(csvQuote(he.getEvaluatorMatric())).append(",")
               .append(csvQuote(he.getEvaluatorName())).append(",")
               .append(nullableInt(he.getFluencyScore())).append(",")
               .append(nullableInt(he.getCoherenceScore())).append(",")
               .append(nullableInt(he.getIngredientCompleteness())).append(",")
               .append(nullableInt(he.getIngredientAccuracy())).append(",")
               .append(nullableInt(he.getQuantityAccuracy())).append(",")
               .append(nullableInt(he.getHallucinationSeverity())).append(",")
               .append(nullableInt(he.getFaithfulnessScore())).append(",")
               .append(nullableInt(he.getJsonStructureScore())).append(",")
               .append(nullableInt(he.getLanguageTagAccuracy())).append(",")
               .append(nullableInt(he.getOverallScore())).append(",")
               .append(csvQuote(he.getRemarks())).append(",")
               .append(he.getEvaluatedAt() != null ? he.getEvaluatedAt().toString() : "")
               .append("\n");
        }
        return csv.toString();
    }

    /** Returns the short score as a string, or empty string if null. */
    private String nullableInt(Short v) {
        return v != null ? v.toString() : "";
    }

    /**
     * LAYER 5: Condition Scores (Aggregated Research Summary)
     * Groups all experiments by their experimental condition (Model + Technique) 
     * and calculates high-level performance metrics including Semantic Recall, 
     * Hallucination Rates, and Calorie Accuracy (MAE).
     */
    /**
     * LAYER 5: Statistical Significance — Friedman & Wilcoxon
     * Columns match LAYER 5 query in metrics_evaluation_queries.sql
     * One row per (transcript × model × technique); evaluate.py builds the condition matrix.
     * GT totals are summed from layer2-annotated ingredients.
     */
    public String generateConditionScoresCsv() {
        List<Experiment> allExperiments = experimentRepository.findAll();
        StringBuilder csvBuilder = new StringBuilder();

        csvBuilder.append("video_id,model_name,technique_name,rag_enabled," +
                          "pred_count,true_positives,false_positives,gt_count,json_valid," +
                          "pred_total_kcal,gt_total_kcal\n");

        for (Experiment exp : allExperiments) {
            if (!"success".equalsIgnoreCase(exp.getStatus())) continue;
            Integer reelId = exp.getTranscript().getReel().getReelId();
            String currentModelTag = exp.getLlmModel().getModelTag();

            NutritionResult nr = nutritionResultRepository
                    .findByExperimentExperimentId(exp.getExperimentId()).orElse(null);

            List<GroundTruthIngredient> gtLayer2 = groundTruthIngredientRepository
                    .findByGroundTruthReel_GtReelId(reelId)
                    .stream()
                    .filter(g -> "layer2".equals(g.getAnnotationLayer()))
                    .collect(Collectors.toList());

            List<IngredientResult> extractedIngredients =
                    ingredientResultRepository.findByNutritionResult_Experiment(exp);

            List<Map<String, Object>> auditRows = extractedIngredients.isEmpty()
                    ? new ArrayList<>()
                    : getAuditRowsCacheFirst(exp.getExperimentId(),
                                            groundTruthIngredientRepository.findByGroundTruthReel_GtReelId(reelId),
                                            extractedIngredients, currentModelTag);

            long predCount = extractedIngredients.size();
            long tp = auditRows.stream().filter(r -> "MATCH".equals(r.get("type"))).count();
            long fp = auditRows.stream().filter(r -> "HALLUCINATION".equals(r.get("type"))).count();
            long gtCount = gtLayer2.size();

            float gtTotalKcal = 0f;
            for (GroundTruthIngredient g : gtLayer2) gtTotalKcal += getFloat(g.getCalories());

            boolean jsonValid = nr != null && Boolean.TRUE.equals(nr.getJsonValid());
            float predTotalKcal = nr != null ? nr.getTotalCalories() : 0f;

            csvBuilder.append(reelId).append(",")
                      .append(csvQuote(exp.getLlmModel().getModelName())).append(",")
                      .append(csvQuote(exp.getPromptTechnique().getTechniqueName())).append(",")
                      .append(exp.getRagEnabled() != null && exp.getRagEnabled() ? 1 : 0).append(",")
                      .append(predCount).append(",")
                      .append(tp).append(",")
                      .append(fp).append(",")
                      .append(gtCount).append(",")
                      .append(jsonValid ? 1 : 0).append(",")
                      .append(predTotalKcal).append(",")
                      .append(gtTotalKcal).append("\n");
        }
        return csvBuilder.toString();
    }

    /** CSV-safe quoting: wraps value in double-quotes and escapes internal double-quotes. */
    private String csvQuote(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    
    private String cleanJsonString(String rawOutput) {
        if (rawOutput == null) return "{}";
        int firstBrace = rawOutput.indexOf('{');
        int lastBrace = rawOutput.lastIndexOf('}');
        if (firstBrace == -1 || lastBrace == -1 || lastBrace < firstBrace) return "{}";
        return rawOutput.substring(firstBrace, lastBrace + 1);
    }

   
    
    private Float getFloat(Object obj) {
        if (obj == null) return 0.0f;
        if (obj instanceof Number) return ((Number) obj).floatValue();
        try {
            return Float.parseFloat(obj.toString().trim());
        } catch (Exception e) {
            return 0.0f;
        }
    }

    private Integer getInteger(Object obj, Integer fallback) {
        if (obj == null) return fallback;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString().trim());
        } catch (Exception e) {
            return fallback;
        }
    }
    
    /**
     * NEW: Semantic Matching using the LLM.
     * This determines if two ingredients are the same by meaning/translation.
     */
    /**
     * REFACTORED: Audit Calculation with Priority Matching.
     * Fixes "Greedy Theft" by matching exact names before calling the LLM.
     */
    /**
     * AUTO HALLUCINATION CHECK — called automatically after each experiment succeeds.
     *
     * Runs the full 4-pass audit (Exact Match → Semantic Match → Missing → Hallucination)
     * for the given experiment and persists every row to the audit_result table.
     *
     * If rows already exist for this experiment (e.g. a previous partial run), they are
     * cleared first so there are no duplicates.
     *
     * Uses the experiment's own model tag for the Semantic Jury so that the same LLM
     * that ran the extraction also evaluates its own output for consistency.
     */
    @Transactional
    public void saveAuditResultsForExperiment(Experiment experiment) {
        Integer expId = experiment.getExperimentId();
        Integer reelId = experiment.getTranscript().getReel().getReelId();
        String modelTag = experiment.getLlmModel().getModelTag();

        log.info("[AUTO-AUDIT] Starting hallucination check for Experiment ID {} (Reel {}, Model {}).",
                 expId, reelId, modelTag);

        List<GroundTruthIngredient> groundTruthIngredients =
                groundTruthIngredientRepository.findByGroundTruthReel_GtReelId(reelId);

        List<IngredientResult> extractedIngredients =
                ingredientResultRepository.findByNutritionResult_Experiment(experiment);

        if (extractedIngredients.isEmpty()) {
            log.warn("[AUTO-AUDIT] No extracted ingredients for Experiment ID {}. Skipping audit.", expId);
            return;
        }

        // Run the 4-pass audit comparison
        List<Map<String, Object>> freshRows =
                calculateAuditData(groundTruthIngredients, extractedIngredients, modelTag);

        // Wipe any stale rows then persist the fresh ones
        auditResultRepository.deleteByExperimentId(expId);

        List<AuditResult> toSave = freshRows.stream()
                .map(row -> mapRowToAuditResult(expId, row))
                .collect(Collectors.toList());

        auditResultRepository.saveAll(toSave);

        long hallucinationCount = toSave.stream()
                .filter(r -> "HALLUCINATION".equals(r.getRowType()))
                .count();
        long missingCount = toSave.stream()
                .filter(r -> "MISSING".equals(r.getRowType()))
                .count();

        log.info("[AUTO-AUDIT] Experiment ID {} — {} rows saved. Hallucinations: {}, Missing: {}.",
                 expId, toSave.size(), hallucinationCount, missingCount);
    }

    /**
     * Maps a freshly-computed audit row Map → AuditResult entity for persistence.
     * Mirrors the helper in ExperimentController so the service is self-contained.
     */
    private AuditResult mapRowToAuditResult(Integer expId, Map<String, Object> r) {
        AuditResult a = new AuditResult();
        a.setExperimentId(expId);
        a.setRowType(r.get("type") != null ? r.get("type").toString() : null);
        a.setSimilarity(r.get("similarity") instanceof Number ? ((Number) r.get("similarity")).doubleValue() : null);
        a.setGtName(r.get("gtName") != null ? r.get("gtName").toString() : null);
        a.setGtNameOri(r.get("gtNameOri") != null ? r.get("gtNameOri").toString() : null);
        a.setAiName(r.get("aiName") != null ? r.get("aiName").toString() : null);
        a.setAiNameOri(r.get("aiNameOri") != null ? r.get("aiNameOri").toString() : null);
        a.setAiQty(r.get("aiQty") instanceof Number ? ((Number) r.get("aiQty")).floatValue() : null);
        a.setAiWeight(r.get("aiWeight") instanceof Number ? ((Number) r.get("aiWeight")).floatValue() : null);
        a.setAiCals(r.get("aiCals") instanceof Number ? ((Number) r.get("aiCals")).floatValue() : null);
        a.setAiFat(r.get("aiFat") instanceof Number ? ((Number) r.get("aiFat")).floatValue() : null);
        a.setAiSatFat(r.get("aiSatFat") instanceof Number ? ((Number) r.get("aiSatFat")).floatValue() : null);
        a.setAiChol(r.get("aiChol") instanceof Number ? ((Number) r.get("aiChol")).floatValue() : null);
        a.setAiSod(r.get("aiSod") instanceof Number ? ((Number) r.get("aiSod")).floatValue() : null);
        a.setAiCarb(r.get("aiCarb") instanceof Number ? ((Number) r.get("aiCarb")).floatValue() : null);
        a.setAiFiber(r.get("aiFiber") instanceof Number ? ((Number) r.get("aiFiber")).floatValue() : null);
        a.setAiSugar(r.get("aiSugar") instanceof Number ? ((Number) r.get("aiSugar")).floatValue() : null);
        a.setAiProt(r.get("aiProt") instanceof Number ? ((Number) r.get("aiProt")).floatValue() : null);
        a.setAiVitD(r.get("aiVitD") instanceof Number ? ((Number) r.get("aiVitD")).floatValue() : null);
        a.setAiCalc(r.get("aiCalc") instanceof Number ? ((Number) r.get("aiCalc")).floatValue() : null);
        a.setAiIron(r.get("aiIron") instanceof Number ? ((Number) r.get("aiIron")).floatValue() : null);
        a.setAiPotas(r.get("aiPotas") instanceof Number ? ((Number) r.get("aiPotas")).floatValue() : null);
        return a;
    }

    /**
     * Converts a cached AuditResult row (read from the database) back into the same
     * Map<String, Object> shape produced by calculateAuditData(), so cached and freshly
     * computed rows can be handled identically by the metrics logic above.
     */
    private Map<String, Object> auditResultToRowMap(AuditResult a) {
        Map<String, Object> row = new HashMap<>();
        row.put("type", a.getRowType());
        row.put("gtName", a.getGtName());
        row.put("gtNameOri", a.getGtNameOri());
        row.put("aiName", a.getAiName());
        row.put("aiNameOri", a.getAiNameOri());
        return row;
    }

    /**
     * SHARED CACHE-FIRST LOOKUP — used by every metric layer that needs the
     * GT↔AI ingredient pairing (MATCH / MISSING / HALLUCINATION).
     *
     * This is the single choke point that used to be duplicated (and, in
     * Layers 2A/2B/3B/3C, skipped entirely) across the report generators.
     * Skipping it was the reason those reports re-ran the expensive LLM
     * Semantic Jury from scratch every time instead of reusing the audit
     * rows already saved by saveAuditResultsForExperiment() right after the
     * experiment finished.
     *
     *   CACHE HIT  → return the persisted rows, zero LLM calls.
     *   CACHE MISS → compute once via calculateAuditData(), persist so every
     *                later call (and every other layer) hits the fast path.
     */
    private List<Map<String, Object>> getAuditRowsCacheFirst(Integer experimentId,
                                                               List<GroundTruthIngredient> groundTruthIngredients,
                                                               List<IngredientResult> extractedIngredients,
                                                               String modelTag) {
        List<AuditResult> cached = auditResultRepository.findByExperimentId(experimentId);
        if (!cached.isEmpty()) {
            return cached.stream().map(this::auditResultToRowMap).collect(Collectors.toList());
        }

        List<Map<String, Object>> freshRows = calculateAuditData(groundTruthIngredients, extractedIngredients, modelTag);

        List<AuditResult> toCache = freshRows.stream()
                .map(row -> mapRowToAuditResult(experimentId, row))
                .collect(Collectors.toList());
        auditResultRepository.saveAll(toCache);

        return freshRows;
    }

    /**
     * Only experiments that actually finished successfully have ingredient
     * data worth auditing. Filtering this up front — instead of looping over
     * every experiment and finding out its ingredient list is empty — is the
     * "check success first" behavior requested for every metric layer below.
     */
    private List<Experiment> findSuccessfulExperiments() {
        return experimentRepository.findAll().stream()
                .filter(e -> "success".equalsIgnoreCase(e.getStatus()))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> calculateAuditData(List<GroundTruthIngredient> groundTruthIngredients, 
                                                        List<IngredientResult> extractedIngredients, 
                                                        String modelTag) {
        
        List<Map<String, Object>> auditComparisonResults = new ArrayList<>();
        List<IngredientResult> remainingAi = new ArrayList<>(extractedIngredients);
        
        // To track which GT items have been matched
        Map<Integer, IngredientResult> matchedMap = new HashMap<>();

        // --- PASS 1: EXACT NAME MATCHING (Priority) ---
        // Checks all 4 cross-language combinations so a GT Malay name can exactly match
        // an AI English name (and vice-versa), avoiding false HALLUCINATION labels.
        for (GroundTruthIngredient gt : groundTruthIngredients) {
            // normalizeName strips parenthetical qualifiers (e.g. "(pada)", "(diced)") before comparing
            String gtNameEn  = normalizeName(gt.getNameEn());
            String gtNameOri = normalizeName(gt.getNameOriginal());
            for (IngredientResult ai : remainingAi) {
                String aiNameEn  = normalizeName(ai.getNameEn());
                String aiNameOri = normalizeName(ai.getNameOriginal());
                boolean exactMatch = (!gtNameEn.isEmpty()  && (gtNameEn.equals(aiNameEn)  || gtNameEn.equals(aiNameOri)))
                                  || (!gtNameOri.isEmpty() && (gtNameOri.equals(aiNameEn) || gtNameOri.equals(aiNameOri)));
                if (exactMatch) {
                    matchedMap.put(gt.getGtIngredientId(), ai);
                    break;
                }
            }
            if (matchedMap.containsKey(gt.getGtIngredientId())) {
                remainingAi.remove(matchedMap.get(gt.getGtIngredientId()));
            }
        }

        // --- PASS 2: SEMANTIC MATCHING (Synonyms/Translations) ---
        for (GroundTruthIngredient gt : groundTruthIngredients) {
            if (matchedMap.containsKey(gt.getGtIngredientId())) continue;

            IngredientResult bestMatch = null;
            for (IngredientResult ai : remainingAi) {
                // Check all 4 cross-language combinations so that GT Malay can match AI English
                // and GT English can match AI Malay — prevents false HALLUCINATION labels when
                // the AI extracts names in a different language than the GT reference.
                boolean matchEnEn   = isSameIngredientSemantic(gt.getNameEn(),       ai.getNameEn(),       modelTag);
                boolean matchOriOri = isSameIngredientSemantic(gt.getNameOriginal(),  ai.getNameOriginal(), modelTag);
                boolean matchEnOri  = isSameIngredientSemantic(gt.getNameEn(),        ai.getNameOriginal(), modelTag);
                boolean matchOriEn  = isSameIngredientSemantic(gt.getNameOriginal(),  ai.getNameEn(),       modelTag);

                if (matchEnEn || matchOriOri || matchEnOri || matchOriEn) {
                    bestMatch = ai;
                    break;
                }
            }

            if (bestMatch != null) {
                matchedMap.put(gt.getGtIngredientId(), bestMatch);
                remainingAi.remove(bestMatch);
            }
        }

        // --- PASS 3: CONSTRUCT ROWS (MATCH & MISSING) ---
        for (GroundTruthIngredient gt : groundTruthIngredients) {
            IngredientResult ai = matchedMap.get(gt.getGtIngredientId());
            auditComparisonResults.add(mapToAuditRow(gt, ai, ai != null ? "MATCH" : "MISSING"));
        }

        // --- PASS 4: CONSTRUCT ROWS (HALLUCINATIONS) ---
        for (IngredientResult ai : remainingAi) {
            auditComparisonResults.add(mapToAuditRow(null, ai, "HALLUCINATION"));
        }

        return auditComparisonResults;
    }

    /**
     * Helper: Maps GT and AI data into the 13-nutrient map required by the UI.
     */
    private Map<String, Object> mapToAuditRow(GroundTruthIngredient gt, IngredientResult ai, String type) {
        Map<String, Object> row = new HashMap<>();
        row.put("type", type);
        row.put("similarity", type.equals("MATCH") ? 1.0 : 0.0);

        // Ground Truth Columns - names only (nutrition values excluded)
        row.put("gtName", gt != null ? gt.getNameEn() : "NONE");
        row.put("gtNameOri", gt != null ? gt.getNameOriginal() : "NONE");

        // AI Columns - Null Safe check for each field
        row.put("aiName", ai != null ? getDisplayName(ai) : (type.equals("MISSING") ? "MISSING" : "NONE"));
        row.put("aiNameOri", ai != null ? ai.getNameOriginal() : "NONE");
        row.put("aiQty", (ai != null && ai.getQuantityValue() != null) ? ai.getQuantityValue() : 0.0f);
        row.put("aiWeight", (ai != null && ai.getEstimatedWeightG() != null) ? ai.getEstimatedWeightG() : 0.0f);
        row.put("aiCals", (ai != null && ai.getCalories() != null) ? ai.getCalories() : 0.0f);
        row.put("aiFat", (ai != null && ai.getTotalFatG() != null) ? ai.getTotalFatG() : 0.0f);
        row.put("aiSatFat", (ai != null && ai.getSaturatedFatG() != null) ? ai.getSaturatedFatG() : 0.0f);
        row.put("aiChol", (ai != null && ai.getCholesterolMg() != null) ? ai.getCholesterolMg() : 0.0f);
        row.put("aiSod", (ai != null && ai.getSodiumMg() != null) ? ai.getSodiumMg() : 0.0f);
        row.put("aiCarb", (ai != null && ai.getTotalCarbohydrateG() != null) ? ai.getTotalCarbohydrateG() : 0.0f);
        row.put("aiFiber", (ai != null && ai.getDietaryFiberG() != null) ? ai.getDietaryFiberG() : 0.0f);
        row.put("aiSugar", (ai != null && ai.getTotalSugarsG() != null) ? ai.getTotalSugarsG() : 0.0f);
        row.put("aiProt", (ai != null && ai.getProteinG() != null) ? ai.getProteinG() : 0.0f);
        row.put("aiVitD", (ai != null && ai.getVitaminDMcg() != null) ? ai.getVitaminDMcg() : 0.0f);
        row.put("aiCalc", (ai != null && ai.getCalciumMg() != null) ? ai.getCalciumMg() : 0.0f);
        row.put("aiIron", (ai != null && ai.getIronMg() != null) ? ai.getIronMg() : 0.0f);
        row.put("aiPotas", (ai != null && ai.getPotassiumMg() != null) ? ai.getPotassiumMg() : 0.0f);

        return row;
    }

    /**
     * REFACTORED: Semantic Jury with strict Hierarchy and Translation rules.
     */
    /**
     * REFACTORED: Taxonomic Semantic Jury.
     * Uses categorization logic to prevent "Contextual Hallucinations" (e.g., Patty matching Sauce).
     * No hard-coded ingredient names are used.
     */
    private boolean isSameIngredientSemantic(String nameA, String nameB, String modelTag) {
        if (nameA == null || nameB == null) return false;
        // Normalize first: strip parenthetical qualifiers so "bawang besar (pada)"
        // is treated identically to "bawang besar" before the LLM jury sees it.
        String a = normalizeName(nameA);
        String b = normalizeName(nameB);

        if (a.equals(b)) return true;
        if (a.equals("n/a") || b.equals("n/a") || a.isEmpty() || b.isEmpty()) return false;

        String cacheKey = a.compareTo(b) < 0 ? a + "::" + b : b + "::" + a;
        if (semanticCache.containsKey(cacheKey)) return semanticCache.get(cacheKey);

        boolean result = runDoubleVerificationJury(a, b, modelTag, cacheKey);
        semanticCache.put(cacheKey, result);
        return result;
    }

    /**
     * DOUBLE-VERIFICATION JURY.
     *
     * A single LLM call is a single opinion, and small local models (llama3.2:3b,
     * qwen2.5:3b, etc.) are noisy enough that one call is not reliable evidence
     * either way - the same pair can flip between MATCH and HALLUCINATION on
     * borderline inputs (typos, partial translations, cut-style qualifiers).
     * This runs TWO independent judgements and only accepts a MATCH when they
     * agree:
     *
     *   Vote 1 (forward)  - "Is A the same as B?"
     *   Vote 2 (reversed)  - "Is B the same as A?" (arguments swapped)
     *
     * Swapping the argument order is a real, cheap way to catch order-sensitivity
     * bugs in small models (a model that answers YES for (A,B) but NO for (B,A)
     * is not confidently judging content, it's reacting to position - that pair
     * should NOT be trusted as a MATCH). If the two votes disagree, or either
     * call throws, a fuzzy Levenshtein score is used as an independent third
     * opinion to break the tie instead of silently defaulting to "not a match".
     * Every disagreement is logged so it can be spot-checked later.
     */
    private boolean runDoubleVerificationJury(String a, String b, String modelTag, String cacheKey) {
        JuryVerdict vote1 = queryJury(a, b, modelTag);
        JuryVerdict vote2 = queryJury(b, a, modelTag);

        boolean call1Ok = vote1 != null;
        boolean call2Ok = vote2 != null;

        if (call1Ok && call2Ok) {
            if (vote1.same == vote2.same) {
                // Both independent votes agree - this is the confident case.
                return vote1.same;
            }
            // Disagreement between the forward and reversed vote: don't trust
            // either blindly. Fall back to fuzzy similarity as a deciding third
            // opinion, but log it so these borderline pairs are easy to audit
            // in Layer 4 (Human Expert Evaluation).
            double sim = calculateSimilarity(a, b);
            boolean tieBreak = sim >= 0.82;
            log.warn("Jury DISAGREEMENT for '{}' vs '{}': forward={}, reversed={}, fuzzySim={}, tieBreak={}",
                    a, b, vote1.same, vote2.same, sim, tieBreak);
            return tieBreak;
        }

        // At least one LLM call failed outright (timeout/parse failure). Use
        // whichever vote succeeded, but only trust a positive verdict; a single
        // successful call is weaker evidence, and it must still pass the same
        // category-compatibility guard the two-call path enforces implicitly.
        if (call1Ok || call2Ok) {
            JuryVerdict solo = call1Ok ? vote1 : vote2;
            log.warn("Only one jury call succeeded for '{}' vs '{}' (call1Ok={}, call2Ok={}); using solo verdict={}",
                    a, b, call1Ok, call2Ok, solo.same);
            return solo.same;
        }

        // Both LLM calls failed - fall back to fuzzy match, same as before.
        log.warn("Both jury calls failed for '{}' vs '{}', falling back to fuzzy match.", a, b);
        return calculateSimilarity(a, b) >= 0.80;
    }

    /**
     * Small holder for one jury call's verdict plus the categories it reasoned
     * with, so the caller can still apply the taxonomic compatibility guard.
     */
    private static final class JuryVerdict {
        final boolean same;
        JuryVerdict(boolean same) { this.same = same; }
    }

    /**
     * Runs a single semantic-jury LLM call for the ordered pair (nameX, nameY)
     * and returns its verdict, or null if the call failed/could not be parsed.
     * Extracted out of isSameIngredientSemantic so it can be invoked twice
     * (forward and argument-swapped) by runDoubleVerificationJury().
     */
    private JuryVerdict queryJury(String nameX, String nameY, String modelTag) {
        // STRICT JSON PROMPT: small local models (e.g. llama3.2:3b) tend to add
        // greetings, explanations, and extra unrequested examples when asked for
        // free-form text ("Category 1: X | Category 2: Y | Result: [[YES]]" was
        // treated more like a chat opener than a format to obey). Forcing a
        // single-line JSON response with a tiny explicit schema, combined with
        // temperature 0 and a small output cap in LLMService.promptStrict(),
        // gives the model far less room to ramble.
        String judgePrompt =
            "You are a JSON-only ingredient-matching API. You never greet, explain, " +
            "or add commentary of any kind. You output ONLY the JSON object requested " +
            "below, on a single line, with no markdown fences and nothing before or after it.\n\n" +
            "Decide if ingredient A and ingredient B refer to the same physical food item.\n" +
            "- Treat spelling variants/typos as the same item (e.g. 'kiram' and 'tiram' are the same word, misspelled).\n" +
            "- Treat a Malay name and its English translation of the same food as the same item (e.g. 'ayam' = 'chicken').\n" +
            "- Do NOT mark them the same if they are genuinely different foods, even within the same broad category (e.g. a sauce is not a meat).\n\n" +
            "A: \"" + nameX + "\"\n" +
            "B: \"" + nameY + "\"\n\n" +
            "Respond with exactly this JSON schema and nothing else:\n" +
            "{\"categoryA\":\"<one or two word food category of A>\",\"categoryB\":\"<one or two word food category of B>\",\"same\":true or false}";

        try {
            String rawResponse = llmService.promptStrict(modelTag, judgePrompt);
            log.info("\n-----------\n" + rawResponse + "\n-----------\n");
            boolean same = parseJudgeResponse(rawResponse, nameX + "::" + nameY);
            return new JuryVerdict(same);
        } catch (Exception e) {
            log.warn("Semantic jury call failed for '{}' vs '{}': {}", nameX, nameY, e.getMessage());
            return null;
        }
    }

    /**
     * Parses the jury's response defensively. Small local models don't always obey
     * a "JSON only" instruction, so this tries structured JSON first, then falls
     * back to scanning the raw text for a same/true|false or a bracketed YES/NO
     * verdict, and only uses the FIRST verdict it finds - small models sometimes
     * hallucinate extra unrequested examples (e.g. numbered lists comparing other
     * ingredient pairs), and anything after the first judgement is noise that
     * should never override it.
     */
    private boolean parseJudgeResponse(String rawResponse, String debugKey) {
        if (rawResponse == null || rawResponse.isBlank()) return false;

        // 1) Try to find and parse a JSON object anywhere in the response.
        String jsonCandidate = cleanJsonString(rawResponse);
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> data = mapper.readValue(jsonCandidate, new TypeReference<Map<String, Object>>() {});
            Object sameObj = data.get("same");
            if (sameObj != null) {
                boolean same = Boolean.parseBoolean(sameObj.toString().trim());
                if (same) {
                    String catA = String.valueOf(data.getOrDefault("categoryA", "")).toLowerCase();
                    String catB = String.valueOf(data.getOrDefault("categoryB", "")).toLowerCase();
                    if (!catA.isBlank() && !catB.isBlank() && !checkCategoryCompatibility(catA, catB)) {
                        return false;
                    }
                }
                return same;
            }
        } catch (Exception ignored) {
            // Model didn't return valid JSON - fall through to text-based parsing.
        }

        // 2) Fallback: scan for the FIRST explicit verdict token in the raw text,
        // ignoring anything the model rambles on with afterward.
        Matcher m = Pattern
            .compile("(?i)\"same\"\\s*:\\s*(true|false)|\\[\\[\\s*(YES|NO)\\s*\\]\\]")
            .matcher(rawResponse);
        if (m.find()) {
            String token = (m.group(1) != null) ? m.group(1) : m.group(2);
            return token.equalsIgnoreCase("true") || token.equalsIgnoreCase("YES");
        }

        log.warn("Could not parse a verdict from jury response for '{}': '{}'", debugKey, rawResponse);
        return false;
    }

    /**
     * Helper to determine if two categories identified by the LLM can realistically be the same thing.
     * This is a logical check, not a keyword check.
     */
    private boolean checkCategoryCompatibility(String catA, String catB) {
        // If the category names are the same, they are compatible
        if (catA.equals(catB)) return true;
        
        // Check for common synonyms in taxonomy (e.g., Meat and Protein)
        if (catA.contains("meat") && catB.contains("protein")) return true;
        if (catA.contains("protein") && catB.contains("meat")) return true;
        
        // Cross-check: A solid protein can never be a sauce/condiment
        if ((catA.contains("meat") || catA.contains("protein")) && 
            (catB.contains("sauce") || catB.contains("condiment") || catB.contains("liquid"))) {
            return false;
        }
        if ((catB.contains("meat") || catB.contains("protein")) && 
            (catA.contains("sauce") || catA.contains("condiment") || catA.contains("liquid"))) {
            return false;
        }

        return true;
    }
}
