package edu.utem.ftmk.llm.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "experiment")
public class Experiment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "experiment_id")
    private Integer experimentId;

    @ManyToOne
    @JoinColumn(name = "transcript_id", nullable = false)
    private Transcript transcript;

    @ManyToOne
    @JoinColumn(name = "model_id", nullable = false)
    private LlmModel llmModel;

    @ManyToOne
    @JoinColumn(name = "technique_id", nullable = false)
    private PromptTechnique promptTechnique;

    @Column(name = "rag_enabled")
    private Boolean ragEnabled = false;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

	public Integer getExperimentId() {
		return experimentId;
	}

	public void setExperimentId(Integer experimentId) {
		this.experimentId = experimentId;
	}

	public Transcript getTranscript() {
		return transcript;
	}

	public void setTranscript(Transcript transcript) {
		this.transcript = transcript;
	}

	public LlmModel getLlmModel() {
		return llmModel;
	}

	public void setLlmModel(LlmModel llmModel) {
		this.llmModel = llmModel;
	}

	public PromptTechnique getPromptTechnique() {
		return promptTechnique;
	}

	public void setPromptTechnique(PromptTechnique promptTechnique) {
		this.promptTechnique = promptTechnique;
	}

	public Boolean getRagEnabled() {
		return ragEnabled;
	}

	public void setRagEnabled(Boolean ragEnabled) {
		this.ragEnabled = ragEnabled;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public LocalDateTime getExecutedAt() {
		return executedAt;
	}

	public void setExecutedAt(java.time.LocalDateTime executedAt) {
	    this.executedAt = executedAt;
	}
    
    
}