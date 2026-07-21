package edu.utem.ftmk.llm.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Maps the human_evaluation table (LAYER 4).
 * One row per experiment — stores Likert-scale scores given by a team member.
 */
@Entity
@Table(name = "human_evaluation")
public class HumanEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "eval_id")
    private Integer evalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "experiment_id", nullable = false)
    private Experiment experiment;

    @Column(name = "evaluator_matric", nullable = false, length = 20)
    private String evaluatorMatric;

    @Column(name = "evaluator_name", nullable = false, length = 100)
    private String evaluatorName;

    // ── Likert scores (1–5) ────────────────────────────────────────────────
    // Using Short with an explicit columnDefinition, because Hibernate's
    // default mapping for Short is SMALLINT, but the actual DB columns are
    // TINYINT(4). columnDefinition pins validation/DDL to TINYINT so it
    // matches the real schema.

    @Column(name = "fluency_score", columnDefinition = "TINYINT")
    private Short fluencyScore;

    @Column(name = "coherence_score", columnDefinition = "TINYINT")
    private Short coherenceScore;

    @Column(name = "ingredient_completeness", columnDefinition = "TINYINT")
    private Short ingredientCompleteness;

    @Column(name = "ingredient_accuracy", columnDefinition = "TINYINT")
    private Short ingredientAccuracy;

    @Column(name = "quantity_accuracy", columnDefinition = "TINYINT")
    private Short quantityAccuracy;

    @Column(name = "hallucination_severity", columnDefinition = "TINYINT")
    private Short hallucinationSeverity;

    @Column(name = "faithfulness_score", columnDefinition = "TINYINT")
    private Short faithfulnessScore;

    @Column(name = "json_structure_score", columnDefinition = "TINYINT")
    private Short jsonStructureScore;

    @Column(name = "language_tag_accuracy", columnDefinition = "TINYINT")
    private Short languageTagAccuracy;

    @Column(name = "overall_score", columnDefinition = "TINYINT")
    private Short overallScore;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "evaluated_at")
    private LocalDateTime evaluatedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ── Getters & Setters ─────────────────────────────────────────────────

    public Integer getEvalId()                        { return evalId; }
    public void    setEvalId(Integer evalId)          { this.evalId = evalId; }

    public Experiment getExperiment()                 { return experiment; }
    public void       setExperiment(Experiment e)     { this.experiment = e; }

    public String getEvaluatorMatric()                { return evaluatorMatric; }
    public void   setEvaluatorMatric(String m)        { this.evaluatorMatric = m; }

    public String getEvaluatorName()                  { return evaluatorName; }
    public void   setEvaluatorName(String n)          { this.evaluatorName = n; }

    public Short  getFluencyScore()                  { return fluencyScore; }
    public void   setFluencyScore(Short v)           { this.fluencyScore = v; }

    public Short  getCoherenceScore()                { return coherenceScore; }
    public void   setCoherenceScore(Short v)         { this.coherenceScore = v; }

    public Short  getIngredientCompleteness()        { return ingredientCompleteness; }
    public void   setIngredientCompleteness(Short v) { this.ingredientCompleteness = v; }

    public Short  getIngredientAccuracy()            { return ingredientAccuracy; }
    public void   setIngredientAccuracy(Short v)     { this.ingredientAccuracy = v; }

    public Short  getQuantityAccuracy()              { return quantityAccuracy; }
    public void   setQuantityAccuracy(Short v)       { this.quantityAccuracy = v; }

    public Short  getHallucinationSeverity()         { return hallucinationSeverity; }
    public void   setHallucinationSeverity(Short v)  { this.hallucinationSeverity = v; }

    public Short  getFaithfulnessScore()             { return faithfulnessScore; }
    public void   setFaithfulnessScore(Short v)      { this.faithfulnessScore = v; }

    public Short  getJsonStructureScore()            { return jsonStructureScore; }
    public void   setJsonStructureScore(Short v)     { this.jsonStructureScore = v; }

    public Short  getLanguageTagAccuracy()           { return languageTagAccuracy; }
    public void   setLanguageTagAccuracy(Short v)    { this.languageTagAccuracy = v; }

    public Short   getOverallScore()                  { return overallScore; }
    public void    setOverallScore(Short v)           { this.overallScore = v; }

    public String  getRemarks()                       { return remarks; }
    public void    setRemarks(String r)               { this.remarks = r; }

    public LocalDateTime getEvaluatedAt()             { return evaluatedAt; }
    public void          setEvaluatedAt(LocalDateTime t){ this.evaluatedAt = t; }

    public LocalDateTime getCreatedAt()               { return createdAt; }
    public void          setCreatedAt(LocalDateTime t){ this.createdAt = t; }
}
