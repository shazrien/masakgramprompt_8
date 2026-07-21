package edu.utem.ftmk.llm.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "influencer")
public class Influencer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "influencer_id")
    private Integer influencerId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "instagram_account", nullable = false, length = 100)
    private String instagramAccount;

    @Column(name = "instagram_url", nullable = false, length = 500)
    private String instagramUrl;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    // Getters and Setters
    public Integer getInfluencerId() {
    	return influencerId; 
    	}
    public void setInfluencerId(Integer influencerId) { 
    	this.influencerId = influencerId; 
    	}

    public String getName() { 
    	return name; 
    	}
    public void setName(String name) {
    	this.name = name; 
    	}

    public String getInstagramAccount() { 
    	return instagramAccount; 
    	}
    public void setInstagramAccount(String instagramAccount) {
    	this.instagramAccount = instagramAccount; 
    	}

    public String getInstagramUrl() {
    	return instagramUrl; 
    	}
    public void setInstagramUrl(String instagramUrl) { 
    	this.instagramUrl = instagramUrl; 
    	}

    public LocalDateTime getCreatedAt() { 
    	return createdAt; 
    	}
    public void setCreatedAt(LocalDateTime createdAt) {
    	this.createdAt = createdAt; 
    	}
}