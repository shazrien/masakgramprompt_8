package edu.utem.ftmk.llm.entity;

import javax.persistence.*;

/**
 * Persisted cache of a single audit comparison row for an experiment.
 *
 * When the user clicks "Inspect", the backend checks whether rows already exist
 * for that experiment_id.  If they do, they are returned directly (fast path).
 * If not, calculateAuditData() is run, every row is saved here, and then the
 * fresh data is returned — so the expensive LLM semantic jury only ever runs once
 * per experiment.
 */
@Entity
@Table(name = "audit_result")
public class AuditResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_result_id")
    private Integer auditResultId;

    /** FK to the experiment this row belongs to. */
    @Column(name = "experiment_id", nullable = false)
    private Integer experimentId;

    /**
     * MATCH | MISSING | HALLUCINATION
     */
    @Column(name = "row_type", nullable = false, length = 20)
    private String rowType;

    // ── Ground Truth columns (names only — nutrition values not stored) ───────
    @Column(name = "gt_name",     length = 200) private String gtName;
    @Column(name = "gt_name_ori", length = 200) private String gtNameOri;

    // ── AI / Extracted columns ────────────────────────────────────────────────
    @Column(name = "ai_name",     length = 200) private String aiName;
    @Column(name = "ai_name_ori", length = 200) private String aiNameOri;
    @Column(name = "ai_qty")      private Float aiQty;
    @Column(name = "ai_weight")   private Float aiWeight;
    @Column(name = "ai_cals")     private Float aiCals;
    @Column(name = "ai_fat")      private Float aiFat;
    @Column(name = "ai_sat_fat")  private Float aiSatFat;
    @Column(name = "ai_chol")     private Float aiChol;
    @Column(name = "ai_sod")      private Float aiSod;
    @Column(name = "ai_carb")     private Float aiCarb;
    @Column(name = "ai_fiber")    private Float aiFiber;
    @Column(name = "ai_sugar")    private Float aiSugar;
    @Column(name = "ai_prot")     private Float aiProt;
    @Column(name = "ai_vit_d")    private Float aiVitD;
    @Column(name = "ai_calc")     private Float aiCalc;
    @Column(name = "ai_iron")     private Float aiIron;
    @Column(name = "ai_potas")    private Float aiPotas;

    @Column(name = "similarity")  private Double similarity;

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public Integer getAuditResultId()               { return auditResultId; }
    public void setAuditResultId(Integer v)         { this.auditResultId = v; }

    public Integer getExperimentId()                { return experimentId; }
    public void setExperimentId(Integer v)          { this.experimentId = v; }

    public String getRowType()                      { return rowType; }
    public void setRowType(String v)                { this.rowType = v; }

    public String getGtName()                       { return gtName; }
    public void setGtName(String v)                 { this.gtName = v; }

    public String getGtNameOri()                    { return gtNameOri; }
    public void setGtNameOri(String v)              { this.gtNameOri = v; }

    public String getAiName()                       { return aiName; }
    public void setAiName(String v)                 { this.aiName = v; }

    public String getAiNameOri()                    { return aiNameOri; }
    public void setAiNameOri(String v)              { this.aiNameOri = v; }

    public Float getAiQty()                         { return aiQty; }
    public void setAiQty(Float v)                   { this.aiQty = v; }

    public Float getAiWeight()                      { return aiWeight; }
    public void setAiWeight(Float v)                { this.aiWeight = v; }

    public Float getAiCals()                        { return aiCals; }
    public void setAiCals(Float v)                  { this.aiCals = v; }

    public Float getAiFat()                         { return aiFat; }
    public void setAiFat(Float v)                   { this.aiFat = v; }

    public Float getAiSatFat()                      { return aiSatFat; }
    public void setAiSatFat(Float v)                { this.aiSatFat = v; }

    public Float getAiChol()                        { return aiChol; }
    public void setAiChol(Float v)                  { this.aiChol = v; }

    public Float getAiSod()                         { return aiSod; }
    public void setAiSod(Float v)                   { this.aiSod = v; }

    public Float getAiCarb()                        { return aiCarb; }
    public void setAiCarb(Float v)                  { this.aiCarb = v; }

    public Float getAiFiber()                       { return aiFiber; }
    public void setAiFiber(Float v)                 { this.aiFiber = v; }

    public Float getAiSugar()                       { return aiSugar; }
    public void setAiSugar(Float v)                 { this.aiSugar = v; }

    public Float getAiProt()                        { return aiProt; }
    public void setAiProt(Float v)                  { this.aiProt = v; }

    public Float getAiVitD()                        { return aiVitD; }
    public void setAiVitD(Float v)                  { this.aiVitD = v; }

    public Float getAiCalc()                        { return aiCalc; }
    public void setAiCalc(Float v)                  { this.aiCalc = v; }

    public Float getAiIron()                        { return aiIron; }
    public void setAiIron(Float v)                  { this.aiIron = v; }

    public Float getAiPotas()                       { return aiPotas; }
    public void setAiPotas(Float v)                 { this.aiPotas = v; }

    public Double getSimilarity()                   { return similarity; }
    public void setSimilarity(Double v)             { this.similarity = v; }
}
