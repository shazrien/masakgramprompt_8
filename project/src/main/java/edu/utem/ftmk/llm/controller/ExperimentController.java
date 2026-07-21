package edu.utem.ftmk.llm.controller;

import edu.utem.ftmk.llm.ExperimentService;
import edu.utem.ftmk.llm.entity.*;
import edu.utem.ftmk.llm.repository.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/experiment")
@CrossOrigin(origins = "*")
public class ExperimentController {

    private final ExperimentRepository experimentRepository;
    private final TranscriptRepository transcriptRepository;
    private final LlmModelRepository llmModelRepository;
    private final PromptTechniqueRepository promptTechniqueRepository;
    private final NutritionResultRepository nutritionResultRepository;
    private final ReelRepository reelRepository;
    private final IngredientResultRepository ingredientResultRepository;
    private final GroundTruthIngredientRepository groundTruthIngredientRepository;
    private final AuditResultRepository auditResultRepository;
    private final HumanEvaluationRepository humanEvaluationRepository;
    private final ExperimentService experimentService;

    public ExperimentController(ExperimentRepository experimentRepository,
                                TranscriptRepository transcriptRepository,
                                LlmModelRepository llmModelRepository,
                                PromptTechniqueRepository promptTechniqueRepository,
                                NutritionResultRepository nutritionResultRepository,
                                ReelRepository reelRepository,
                                IngredientResultRepository ingredientResultRepository,
                                GroundTruthIngredientRepository groundTruthIngredientRepository,
                                AuditResultRepository auditResultRepository,
                                HumanEvaluationRepository humanEvaluationRepository,
                                ExperimentService experimentService) {
        this.experimentRepository = experimentRepository;
        this.transcriptRepository = transcriptRepository;
        this.llmModelRepository = llmModelRepository;
        this.promptTechniqueRepository = promptTechniqueRepository;
        this.nutritionResultRepository = nutritionResultRepository;
        this.reelRepository = reelRepository;
        this.ingredientResultRepository = ingredientResultRepository;
        this.groundTruthIngredientRepository = groundTruthIngredientRepository;
        this.auditResultRepository = auditResultRepository;
        this.humanEvaluationRepository = humanEvaluationRepository;
        this.experimentService = experimentService;
    }

    /**
     * GET /api/experiments
     * Lists all experiments.
     */
    @GetMapping("/experiments")
    public List<Experiment> getAllExperiments() {
        return experimentRepository.findAll();
    }

    /**
     * POST /api/experiment/create-and-run
     * Creates a new experiment and runs it asynchronously.
     */
    @PostMapping("/create-and-run")
    public ResponseEntity<?> createAndRunExperiment(@RequestBody Map<String, Object> payload) {
        Integer modelId = getInteger(payload.get("modelId"));
        Integer techniqueId = getInteger(payload.get("techniqueId"));
        Boolean ragEnabled = getBoolean(payload.get("ragEnabled"), false);

        experimentService.runExperiment(modelId, techniqueId, ragEnabled);
        return ResponseEntity.accepted().body("Batch process started.");
    }

    // 4. ADD THIS STOP ENDPOINT
    @PostMapping("/stop")
    public ResponseEntity<?> stopProcess() {
        experimentService.stopExperiment();
        return ResponseEntity.ok("Stop signal sent to server.");
    }

    /**
     * GET /api/experiment/{id}/results
     * Retrieves extraction results of a specific experiment.
     */
    /**
     * Unified endpoint: Returns Experiment, Nutrition, and Ingredients all in one call.
     * This replaces both previous versions.
     */
    @GetMapping("/{id}/results")
    public ResponseEntity<?> getExperimentResults(@PathVariable Integer id) {
        Optional<Experiment> experimentOpt = experimentRepository.findById(id);
        
        if (experimentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Experiment not found");
        }

        Experiment experiment = experimentOpt.get();
        
        // Fetch transcript name
        String transcriptName = (experiment.getTranscript() != null) ? experiment.getTranscript().getFileName() : "N/A";
        
        Map<String, Object> response = new HashMap<>();
        response.put("experiment", experiment);
        response.put("transcriptName", transcriptName);

        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/export")
    public void handleExport(@RequestBody Map<String, String> payload, HttpServletResponse response) throws IOException {
        String queryLabel = payload.get("queryLabel"); 
        String fileName = payload.get("fileName");

        response.setContentType("text/csv");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

        String csvContent = "";
        if (queryLabel.equals("LAYER 1A")) csvContent = experimentService.generateExactMatchCsv();
        else if (queryLabel.equals("LAYER 1B")) csvContent = experimentService.generateTextSimilarityCsv();
        else if (queryLabel.equals("LAYER 2A")) csvContent = experimentService.generateNumericQuantityCsv();
        else if (queryLabel.equals("LAYER 2B")) csvContent = experimentService.generateItemizedIngredientCsv();
        else if (queryLabel.equals("LAYER 2C")) csvContent = experimentService.generateGlobalNutritionCsv();
        else if (queryLabel.equals("LAYER 3A")) csvContent = experimentService.generateJsonValidityCsv();
        else if (queryLabel.equals("LAYER 3B")) csvContent = experimentService.generateHallucinationCsv();
        else if (queryLabel.equals("LAYER 3C")) csvContent = experimentService.generateIngredientDetectionCsv();
        else if (queryLabel.equals("LAYER 4"))  csvContent = experimentService.generateHumanEvaluationCsv();
        else if (queryLabel.equals("LAYER 5"))  csvContent = experimentService.generateConditionScoresCsv();

        response.getWriter().write(csvContent);
        response.getWriter().flush();
    }

    /**
     * GET /experiment/{expId}/evaluation
     * Returns the existing human evaluation for an experiment, if any exists,
     * so the form can be pre-filled for editing. Returns 204 No Content when
     * no evaluation has been entered yet.
     */
    @GetMapping("/{expId}/evaluation")
    public ResponseEntity<?> getEvaluation(@PathVariable Integer expId) {
        return humanEvaluationRepository.findByExperiment_ExperimentId(expId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * POST /experiment/{expId}/evaluation
     * Creates the human evaluation for an experiment, or updates it if one
     * already exists (one evaluation per experiment).
     */
    @PostMapping("/{expId}/evaluation")
    @Transactional
    public ResponseEntity<?> submitEvaluation(@PathVariable Integer expId, @RequestBody Map<String, Object> payload) {
        Experiment experiment = experimentRepository.findById(expId).orElse(null);
        if (experiment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Experiment not found");
        }

        String evaluatorMatric = payload.get("evaluatorMatric") != null ? payload.get("evaluatorMatric").toString().trim() : "";
        String evaluatorName = payload.get("evaluatorName") != null ? payload.get("evaluatorName").toString().trim() : "";
        if (evaluatorMatric.isEmpty() || evaluatorName.isEmpty()) {
            return ResponseEntity.badRequest().body("evaluatorMatric and evaluatorName are required");
        }

        HumanEvaluation eval = humanEvaluationRepository.findByExperiment_ExperimentId(expId)
                .orElseGet(HumanEvaluation::new);

        boolean isNew = eval.getEvalId() == null;

        eval.setExperiment(experiment);
        eval.setEvaluatorMatric(evaluatorMatric);
        eval.setEvaluatorName(evaluatorName);
        eval.setFluencyScore(getShort(payload.get("fluencyScore")));
        eval.setCoherenceScore(getShort(payload.get("coherenceScore")));
        eval.setIngredientCompleteness(getShort(payload.get("ingredientCompleteness")));
        eval.setIngredientAccuracy(getShort(payload.get("ingredientAccuracy")));
        eval.setQuantityAccuracy(getShort(payload.get("quantityAccuracy")));
        eval.setHallucinationSeverity(getShort(payload.get("hallucinationSeverity")));
        eval.setFaithfulnessScore(getShort(payload.get("faithfulnessScore")));
        eval.setJsonStructureScore(getShort(payload.get("jsonStructureScore")));
        eval.setLanguageTagAccuracy(getShort(payload.get("languageTagAccuracy")));
        eval.setOverallScore(getShort(payload.get("overallScore")));
        eval.setRemarks(payload.get("remarks") != null ? payload.get("remarks").toString() : null);
        eval.setEvaluatedAt(LocalDateTime.now());
        if (isNew) {
            eval.setCreatedAt(LocalDateTime.now());
        }

        HumanEvaluation saved = humanEvaluationRepository.save(eval);
        return ResponseEntity.ok(saved);
    }

    private Short getShort(Object obj) {
        if (obj == null || obj.toString().trim().isEmpty()) return null;
        try {
            return Short.parseShort(obj.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Integer getInteger(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean getBoolean(Object obj, Boolean fallback) {
        if (obj == null) return fallback;
        if (obj instanceof Boolean) return (Boolean) obj;
        return Boolean.parseBoolean(obj.toString().trim());
    }
    

    @GetMapping("/history/{reelId}")
    public List<Map<String, Object>> getReelHistory(@PathVariable Integer reelId) {
        return experimentRepository.findAll().stream()
            .filter(e -> e.getTranscript() != null && 
                         e.getTranscript().getReel() != null && 
                         e.getTranscript().getReel().getReelId().equals(reelId))
            .map(exp -> {
                Map<String, Object> map = new HashMap<>();
                map.put("expId", exp.getExperimentId());

                // Resolve the actual Reel ID from Experiment → Transcript → Reel
                Integer resolvedReelId = null;
                if (exp.getTranscript() != null && exp.getTranscript().getReel() != null) {
                    resolvedReelId = exp.getTranscript().getReel().getReelId();
                }
                map.put("reelId", resolvedReelId);
                
                // Safe Model and Technique extraction
                map.put("model", exp.getLlmModel() != null ? exp.getLlmModel().getModelName() : "Deleted Model");
                map.put("modelId", exp.getLlmModel() != null ? exp.getLlmModel().getModelId() : null);
                map.put("technique", exp.getPromptTechnique() != null ? exp.getPromptTechnique().getTechniqueName() : "Deleted Technique");
                map.put("techniqueId", exp.getPromptTechnique() != null ? exp.getPromptTechnique().getTechniqueId() : null);
                map.put("status", exp.getStatus());
                
                // Safe AI Calories Check
                NutritionResult nr = nutritionResultRepository.findByExperimentExperimentId(exp.getExperimentId()).orElse(null);
                float aiCals = (nr != null && nr.getTotalCalories() != null) ? nr.getTotalCalories() : 0.0f;
                map.put("aiCals", aiCals);

                
                return map;
            }).collect(Collectors.toList());
    }

    /**
     * GET /experiment/audit-details/{expId}
     *
     * Cache-first strategy:
     *   1. Check audit_result table for existing rows for this experiment.
     *   2. CACHE HIT  → return immediately (no LLM calls, instant response).
     *   3. CACHE MISS → run calculateAuditData() (LLM semantic jury), persist
     *                   every row to DB, then return the fresh data.
     *
     * Optional ?refresh=true forces re-computation even when a cache exists
     * (useful after correcting Ground Truth data).
     */
    @GetMapping("/audit-details/{expId}")
    @Transactional
    public ResponseEntity<List<Map<String, Object>>> getAuditDetails(
            @PathVariable Integer expId,
            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh) {

        Experiment experimentRecord = experimentRepository.findById(expId)
                .orElseThrow(() -> new RuntimeException("Experiment not found: " + expId));

        // ── CACHE HIT ────────────────────────────────────────────────────────
        if (!refresh) {
            List<AuditResult> cached = auditResultRepository.findByExperimentId(expId);
            if (!cached.isEmpty()) {
                return ResponseEntity.ok(cached.stream()
                        .map(this::auditResultToMap)
                        .collect(java.util.stream.Collectors.toList()));
            }
        }

        // ── CACHE MISS — run full LLM verification ────────────────────────────
        String modelTag = "llama3.2:3b";

        List<GroundTruthIngredient> groundTruthIngredients = groundTruthIngredientRepository
                .findByGroundTruthReel_GtReelId(experimentRecord.getTranscript().getReel().getReelId());

        List<IngredientResult> extractedIngredients = ingredientResultRepository
                .findByNutritionResult_Experiment(experimentRecord);

        List<Map<String, Object>> freshRows =
                experimentService.calculateAuditData(groundTruthIngredients, extractedIngredients, modelTag);

        // ── PERSIST to DB ─────────────────────────────────────────────────────
        if (refresh) {
            auditResultRepository.deleteByExperimentId(expId);
        }

        List<AuditResult> toSave = freshRows.stream()
                .map(row -> mapToAuditResult(expId, row))
                .collect(java.util.stream.Collectors.toList());

        auditResultRepository.saveAll(toSave);

        return ResponseEntity.ok(freshRows);
    }

    // ── Conversion helpers ────────────────────────────────────────────────────

    /** Convert a persisted AuditResult entity back to the Map the frontend expects. */
    private Map<String, Object> auditResultToMap(AuditResult a) {
        Map<String, Object> m = new HashMap<>();
        m.put("type",      a.getRowType());
        m.put("similarity", a.getSimilarity());
        m.put("gtName",    nvl(a.getGtName(),    "NONE"));
        m.put("gtNameOri", nvl(a.getGtNameOri(), "NONE"));
        // GT nutrition values excluded — only AI results are returned
        m.put("aiName",    nvl(a.getAiName(),    "NONE"));
        m.put("aiNameOri", nvl(a.getAiNameOri(), "NONE"));
        m.put("aiQty",     nvl(a.getAiQty(),     0.0f));
        m.put("aiWeight",  nvl(a.getAiWeight(),  0.0f));
        m.put("aiCals",    nvl(a.getAiCals(),    0.0f));
        m.put("aiFat",     nvl(a.getAiFat(),     0.0f));
        m.put("aiSatFat",  nvl(a.getAiSatFat(),  0.0f));
        m.put("aiChol",    nvl(a.getAiChol(),    0.0f));
        m.put("aiSod",     nvl(a.getAiSod(),     0.0f));
        m.put("aiCarb",    nvl(a.getAiCarb(),    0.0f));
        m.put("aiFiber",   nvl(a.getAiFiber(),   0.0f));
        m.put("aiSugar",   nvl(a.getAiSugar(),   0.0f));
        m.put("aiProt",    nvl(a.getAiProt(),    0.0f));
        m.put("aiVitD",    nvl(a.getAiVitD(),    0.0f));
        m.put("aiCalc",    nvl(a.getAiCalc(),    0.0f));
        m.put("aiIron",    nvl(a.getAiIron(),    0.0f));
        m.put("aiPotas",   nvl(a.getAiPotas(),   0.0f));
        return m;
    }

    /** Map a freshly-computed audit row Map → AuditResult entity for persistence. */
    private AuditResult mapToAuditResult(Integer expId, Map<String, Object> r) {
        AuditResult a = new AuditResult();
        a.setExperimentId(expId);
        a.setRowType(str(r.get("type")));
        a.setSimilarity(toDouble(r.get("similarity")));
        a.setGtName(str(r.get("gtName")));
        a.setGtNameOri(str(r.get("gtNameOri")));
        // GT nutrition values excluded — only AI results are persisted
        a.setAiName(str(r.get("aiName")));
        a.setAiNameOri(str(r.get("aiNameOri")));
        a.setAiQty(toFloat(r.get("aiQty")));
        a.setAiWeight(toFloat(r.get("aiWeight")));
        a.setAiCals(toFloat(r.get("aiCals")));
        a.setAiFat(toFloat(r.get("aiFat")));
        a.setAiSatFat(toFloat(r.get("aiSatFat")));
        a.setAiChol(toFloat(r.get("aiChol")));
        a.setAiSod(toFloat(r.get("aiSod")));
        a.setAiCarb(toFloat(r.get("aiCarb")));
        a.setAiFiber(toFloat(r.get("aiFiber")));
        a.setAiSugar(toFloat(r.get("aiSugar")));
        a.setAiProt(toFloat(r.get("aiProt")));
        a.setAiVitD(toFloat(r.get("aiVitD")));
        a.setAiCalc(toFloat(r.get("aiCalc")));
        a.setAiIron(toFloat(r.get("aiIron")));
        a.setAiPotas(toFloat(r.get("aiPotas")));
        return a;
    }

    private <T> T nvl(T value, T fallback) { return value != null ? value : fallback; }
    private String str(Object o)           { return o != null ? o.toString() : null; }
    private Float  toFloat(Object o)       { return o instanceof Number ? ((Number) o).floatValue()  : null; }
    private Double toDouble(Object o)      { return o instanceof Number ? ((Number) o).doubleValue() : null; }

    /**
     * GET /api/experiment/reels
     * Returns every reel ID currently in the database, sorted ascending.
     * Used by the frontend to build reel dropdowns dynamically instead of
     * assuming a fixed count (e.g. 1-50).
     */
    @GetMapping("/reels")
    public List<Integer> getReelIds() {
        return reelRepository.findAll().stream()
            .map(Reel::getReelId)
            .sorted()
            .collect(Collectors.toList());
    }

    @GetMapping("/models")
    public List<LlmModel> getModels() { return llmModelRepository.findAll(); }

    @GetMapping("/techniques")
    public List<PromptTechnique> getTechniques() { return promptTechniqueRepository.findAll(); }
}