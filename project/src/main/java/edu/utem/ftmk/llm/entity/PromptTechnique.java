package edu.utem.ftmk.llm.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "prompt_technique")
public class PromptTechnique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "technique_id")
    private Integer techniqueId;

    @Column(name = "technique_name", nullable = false, length = 50)
    private String techniqueName;

    @Column(name = "system_prompt_file", nullable = false, length = 500)
    private String systemPromptFile;

    @Column(name = "user_prompt_file", nullable = false, length = 500)
    private String userPromptFile;

    @Column(name = "prompt_version", nullable = false, length = 10)
    private String promptVersion;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

	public Integer getTechniqueId() {
		return techniqueId;
	}

	public void setTechniqueId(Integer techniqueId) {
		this.techniqueId = techniqueId;
	}

	public String getTechniqueName() {
		return techniqueName;
	}

	public void setTechniqueName(String techniqueName) {
		this.techniqueName = techniqueName;
	}

	public String getSystemPromptFile() {
		return systemPromptFile;
	}

	public void setSystemPromptFile(String systemPromptFile) {
		this.systemPromptFile = systemPromptFile;
	}

	public String getUserPromptFile() {
		return userPromptFile;
	}

	public void setUserPromptFile(String userPromptFile) {
		this.userPromptFile = userPromptFile;
	}

	public String getPromptVersion() {
		return promptVersion;
	}

	public void setPromptVersion(String promptVersion) {
		this.promptVersion = promptVersion;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
    
}