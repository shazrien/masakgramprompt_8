package edu.utem.ftmk.llm.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audio_file")
public class AudioFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audio_id")
    private Integer audioId;

    @OneToOne
    @JoinColumn(name = "reel_id", nullable = false)
    private Reel reel;

    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_created_at", nullable = false)
    private LocalDateTime fileCreatedAt;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "file_format", nullable = false, length = 10)
    private String fileFormat;

    @Column(name = "reel_audio_consistent", nullable = false)
    private Boolean reelAudioConsistent;

    @Column(name = "verified_by_matric", nullable = false, length = 20)
    private String verifiedByMatric;

    @Column(name = "verified_by_name", nullable = false, length = 100)
    private String verifiedByName;

    @Column(name = "verified_at", insertable = false, updatable = false)
    private LocalDateTime verifiedAt;

    // Getters and Setters
    public Integer getAudioId() { 
    	return audioId; 
    	}
    public void setAudioId(Integer audioId) {
    	this.audioId = audioId; 
    	}

    public Reel getReel() { 
    	return reel; 
    	}
    public void setReel(Reel reel) { 
    	this.reel = reel; 
    	}

    public String getFileName() { 
    	return fileName; 
    	}
    public void setFileName(String fileName) { 
    	this.fileName = fileName; 
    	}

    public String getFilePath() { 
    	return filePath;
    	}
    public void setFilePath(String filePath) {
    	this.filePath = filePath; 
    	}

    public LocalDateTime getFileCreatedAt() { 
    	return fileCreatedAt; 
    	}
    public void setFileCreatedAt(LocalDateTime fileCreatedAt) {
    	this.fileCreatedAt = fileCreatedAt; 
    	}

    public Long getFileSizeBytes() {
    	return fileSizeBytes; 
    	}
    public void setFileSizeBytes(Long fileSizeBytes) {
    	this.fileSizeBytes = fileSizeBytes; 
    	}

    public String getFileFormat() { 
    	return fileFormat; 
    	}
    public void setFileFormat(String fileFormat) {
    	this.fileFormat = fileFormat; 
    	}

    public Boolean getReelAudioConsistent() {
    	return reelAudioConsistent; 
    	}
    public void setReelAudioConsistent(Boolean reelAudioConsistent) {
    	this.reelAudioConsistent = reelAudioConsistent; 
    	}

    public String getVerifiedByMatric() { 
    	return verifiedByMatric; 
    	}
    public void setVerifiedByMatric(String verifiedByMatric) {
    	this.verifiedByMatric = verifiedByMatric; 
    	}

    public String getVerifiedByName() { 
    	return verifiedByName; 
    	}
    public void setVerifiedByName(String verifiedByName) {
    	this.verifiedByName = verifiedByName; 
    	}

    public LocalDateTime getVerifiedAt() {
    	return verifiedAt; 
    	}
    public void setVerifiedAt(LocalDateTime verifiedAt) {
    	this.verifiedAt = verifiedAt;
    	}
}