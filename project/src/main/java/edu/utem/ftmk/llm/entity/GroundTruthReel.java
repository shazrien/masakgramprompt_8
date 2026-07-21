package edu.utem.ftmk.llm.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ground_truth_reel")
public class GroundTruthReel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "gt_reel_id")
    private Integer gtReelId;

    @OneToOne
    @JoinColumn(name = "transcript_id", nullable = false)
    private Transcript transcript;

    @Column(name = "annotator_matric", nullable = false, length = 20)
    private String annotatorMatric;

    @Column(name = "annotator_name", nullable = false, length = 100)
    private String annotatorName;

    @Column(name = "annotated_at", insertable = false, updatable = false)
    private LocalDateTime annotatedAt;

	public Integer getGtReelId() {
		return gtReelId;
	}

	public void setGtReelId(Integer gtReelId) {
		this.gtReelId = gtReelId;
	}

	public Transcript getTranscript() {
		return transcript;
	}

	public void setTranscript(Transcript transcript) {
		this.transcript = transcript;
	}

	public String getAnnotatorMatric() {
		return annotatorMatric;
	}

	public void setAnnotatorMatric(String annotatorMatric) {
		this.annotatorMatric = annotatorMatric;
	}

	public String getAnnotatorName() {
		return annotatorName;
	}

	public void setAnnotatorName(String annotatorName) {
		this.annotatorName = annotatorName;
	}

	public LocalDateTime getAnnotatedAt() {
		return annotatedAt;
	}

	public void setAnnotatedAt(LocalDateTime annotatedAt) {
		this.annotatedAt = annotatedAt;
	}
    
    
}