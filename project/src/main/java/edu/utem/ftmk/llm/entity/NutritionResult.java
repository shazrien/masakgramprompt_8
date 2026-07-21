package edu.utem.ftmk.llm.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "nutrition_result")
public class NutritionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "result_id")
    private Integer resultId;

    @OneToOne
    @JoinColumn(name = "experiment_id", nullable = false)
    private Experiment experiment;

    @Column(name = "recipe_name", nullable = true)
    private String recipeName;

    @Column(name = "servings_estimated", nullable = false)
    private Integer servingsEstimated;

    @Column(name = "serving_calories", nullable = false)
    private Float servingCalories;

    @Column(name = "serving_total_fat_g", nullable = false)
    private Float servingTotalFatG;

    @Column(name = "serving_saturated_fat_g", nullable = false)
    private Float servingSaturatedFatG;

    @Column(name = "serving_cholesterol_mg", nullable = false)
    private Float servingCholesterolMg;

    @Column(name = "serving_sodium_mg", nullable = false)
    private Float servingSodiumMg;

    @Column(name = "serving_carbohydrate_g", nullable = false)
    private Float servingCarbohydrateG;

    @Column(name = "serving_fiber_g", nullable = false)
    private Float servingFiberG;

    @Column(name = "serving_sugars_g", nullable = false)
    private Float servingSugarsG;

    @Column(name = "serving_protein_g", nullable = false)
    private Float servingProteinG;

    @Column(name = "serving_vitamin_d_mcg", nullable = false)
    private Float servingVitaminDMcg;

    @Column(name = "serving_calcium_mg", nullable = false)
    private Float servingCalciumMg = 0.0f;

    @Column(name = "serving_iron_mg", nullable = false)
    private Float servingIronMg;

    @Column(name = "serving_potassium_mg", nullable = false)
    private Float servingPotassiumMg;

    @Column(name = "total_calories", nullable = false)
    private Float totalCalories;

    @Column(name = "total_fat_g", nullable = false)
    private Float totalFatG;

    @Column(name = "total_saturated_fat_g", nullable = false)
    private Float totalSaturatedFatG;

    @Column(name = "total_cholesterol_mg", nullable = false)
    private Float totalCholesterolMg;

    @Column(name = "total_sodium_mg", nullable = false)
    private Float totalSodiumMg;

    @Column(name = "total_carbohydrate_g", nullable = false)
    private Float totalCarbohydrateG;

    @Column(name = "total_fiber_g", nullable = false)
    private Float totalFiberG;

    @Column(name = "total_sugars_g", nullable = false)
    private Float totalSugarsG;

    @Column(name = "total_protein_g", nullable = false)
    private Float totalProteinG;

    @Column(name = "total_vitamin_d_mcg", nullable = false)
    private Float totalVitaminDMcg;

    @Column(name = "total_calcium_mg", nullable = false)
    private Float totalCalciumMg;

    @Column(name = "total_iron_mg", nullable = false)
    private Float totalIronMg = 0.0f;

    @Column(name = "total_potassium_mg", nullable = false)
    private Float totalPotassiumMg;

    @Column(name = "raw_json_output", nullable = false, columnDefinition = "TEXT")
    private String rawJsonOutput;

    @Column(name = "json_valid", nullable = false)
    private Boolean jsonValid;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

	public Integer getResultId() {
		return resultId;
	}

	public void setResultId(Integer resultId) {
		this.resultId = resultId;
	}

	public Experiment getExperiment() {
		return experiment;
	}

	public void setExperiment(Experiment experiment) {
		this.experiment = experiment;
	}

	public String getRecipeName() {
		return recipeName;
	}

	public void setRecipeName(String recipeName) {
		this.recipeName = recipeName;
	}

	public Integer getServingsEstimated() {
		return servingsEstimated;
	}

	public void setServingsEstimated(Integer servingsEstimated) {
		this.servingsEstimated = servingsEstimated;
	}

	public Float getServingCalories() {
		return servingCalories;
	}

	public void setServingCalories(Float servingCalories) {
		this.servingCalories = servingCalories;
	}

	public Float getServingTotalFatG() {
		return servingTotalFatG;
	}

	public void setServingTotalFatG(Float servingTotalFatG) {
		this.servingTotalFatG = servingTotalFatG;
	}

	public Float getServingSaturatedFatG() {
		return servingSaturatedFatG;
	}

	public void setServingSaturatedFatG(Float servingSaturatedFatG) {
		this.servingSaturatedFatG = servingSaturatedFatG;
	}

	public Float getServingCholesterolMg() {
		return servingCholesterolMg;
	}

	public void setServingCholesterolMg(Float servingCholesterolMg) {
		this.servingCholesterolMg = servingCholesterolMg;
	}

	public Float getServingSodiumMg() {
		return servingSodiumMg;
	}

	public void setServingSodiumMg(Float servingSodiumMg) {
		this.servingSodiumMg = servingSodiumMg;
	}

	public Float getServingCarbohydrateG() {
		return servingCarbohydrateG;
	}

	public void setServingCarbohydrateG(Float servingCarbohydrateG) {
		this.servingCarbohydrateG = servingCarbohydrateG;
	}

	public Float getServingFiberG() {
		return servingFiberG;
	}

	public void setServingFiberG(Float servingFiberG) {
		this.servingFiberG = servingFiberG;
	}

	public Float getServingSugarsG() {
		return servingSugarsG;
	}

	public void setServingSugarsG(Float servingSugarsG) {
		this.servingSugarsG = servingSugarsG;
	}

	public Float getServingProteinG() {
		return servingProteinG;
	}

	public void setServingProteinG(Float servingProteinG) {
		this.servingProteinG = servingProteinG;
	}

	public Float getServingVitaminDMcg() {
		return servingVitaminDMcg;
	}

	public void setServingVitaminDMcg(Float servingVitaminDMcg) {
		this.servingVitaminDMcg = servingVitaminDMcg;
	}

	public Float getServingCalciumMg() {
		return servingCalciumMg;
	}

	public void setServingCalciumMg(Float value) {
        this.servingCalciumMg = (value != null) ? value : 0.0f;
    }

	public Float getServingIronMg() {
		return servingIronMg;
	}

	public void setServingIronMg(Float servingIronMg) {
		this.servingIronMg = servingIronMg;
	}

	public Float getServingPotassiumMg() {
		return servingPotassiumMg;
	}

	public void setServingPotassiumMg(Float servingPotassiumMg) {
		this.servingPotassiumMg = servingPotassiumMg;
	}

	public Float getTotalCalories() {
		return totalCalories;
	}

	public void setTotalCalories(Float totalCalories) {
		this.totalCalories = totalCalories;
	}

	public Float getTotalFatG() {
		return totalFatG;
	}

	public void setTotalFatG(Float totalFatG) {
		this.totalFatG = totalFatG;
	}

	public Float getTotalSaturatedFatG() {
		return totalSaturatedFatG;
	}

	public void setTotalSaturatedFatG(Float totalSaturatedFatG) {
		this.totalSaturatedFatG = totalSaturatedFatG;
	}

	public Float getTotalCholesterolMg() {
		return totalCholesterolMg;
	}

	public void setTotalCholesterolMg(Float totalCholesterolMg) {
		this.totalCholesterolMg = totalCholesterolMg;
	}

	public Float getTotalSodiumMg() {
		return totalSodiumMg;
	}

	public void setTotalSodiumMg(Float totalSodiumMg) {
		this.totalSodiumMg = totalSodiumMg;
	}

	public Float getTotalCarbohydrateG() {
		return totalCarbohydrateG;
	}

	public void setTotalCarbohydrateG(Float totalCarbohydrateG) {
		this.totalCarbohydrateG = totalCarbohydrateG;
	}

	public Float getTotalFiberG() {
		return totalFiberG;
	}

	public void setTotalFiberG(Float totalFiberG) {
		this.totalFiberG = totalFiberG;
	}

	public Float getTotalSugarsG() {
		return totalSugarsG;
	}

	public void setTotalSugarsG(Float totalSugarsG) {
		this.totalSugarsG = totalSugarsG;
	}

	public Float getTotalProteinG() {
		return totalProteinG;
	}

	public void setTotalProteinG(Float totalProteinG) {
		this.totalProteinG = totalProteinG;
	}

	public Float getTotalVitaminDMcg() {
		return totalVitaminDMcg;
	}

	public void setTotalVitaminDMcg(Float totalVitaminDMcg) {
		this.totalVitaminDMcg = totalVitaminDMcg;
	}

	public Float getTotalCalciumMg() {
		return totalCalciumMg;
	}

	public void setTotalCalciumMg(Float totalCalciumMg) {
		this.totalCalciumMg = totalCalciumMg;
	}

	public Float getTotalIronMg() {
		return totalIronMg;
	}

	public void setTotalIronMg(Float totalIronMg) {
		this.totalIronMg = totalIronMg;
	}

	public Float getTotalPotassiumMg() {
		return totalPotassiumMg;
	}

	public void setTotalPotassiumMg(Float totalPotassiumMg) {
		this.totalPotassiumMg = totalPotassiumMg;
	}

	public String getRawJsonOutput() {
		return rawJsonOutput;
	}

	public void setRawJsonOutput(String rawJsonOutput) {
		this.rawJsonOutput = rawJsonOutput;
	}

	public Boolean getJsonValid() {
		return jsonValid;
	}

	public void setJsonValid(Boolean jsonValid) {
		this.jsonValid = jsonValid;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
 
    
}