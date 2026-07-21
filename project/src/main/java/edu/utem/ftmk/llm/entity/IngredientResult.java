package edu.utem.ftmk.llm.entity;

import javax.persistence.*;

@Entity
@Table(name = "ingredient_result")
public class IngredientResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ingredient_id")
    private Integer ingredientId;

    @ManyToOne
    @JoinColumn(name = "result_id", nullable = false)
    private NutritionResult nutritionResult;

    @Column(name = "name_original", nullable = false, length = 200)
    private String nameOriginal;

    @Column(name = "name_en", nullable = false, length = 200)
    private String nameEn;

    @Column(name = "quantity_value", nullable = false)
    private Float quantityValue;

    @Column(name = "unit_original", nullable = false, length = 100)
    private String unitOriginal;

    @Column(name = "unit_en", nullable = false, length = 100)
    private String unitEn;

    @Column(name = "estimated_weight_g", nullable = false)
    private Float estimatedWeightG;

    @Column(name = "calories", nullable = false)
    private Float calories;

    @Column(name = "total_fat_g", nullable = false)
    private Float totalFatG;

    @Column(name = "saturated_fat_g", nullable = false)
    private Float saturatedFatG;

    @Column(name = "cholesterol_mg", nullable = false)
    private Float cholesterolMg;

    @Column(name = "sodium_mg", nullable = false)
    private Float sodiumMg;

    @Column(name = "total_carbohydrate_g", nullable = false)
    private Float totalCarbohydrateG;

    @Column(name = "dietary_fiber_g", nullable = false)
    private Float dietaryFiberG;

    @Column(name = "total_sugars_g", nullable = false)
    private Float totalSugarsG;

    @Column(name = "protein_g", nullable = false)
    private Float proteinG;

    @Column(name = "vitamin_d_mcg", nullable = false)
    private Float vitaminDMcg;

    @Column(name = "calcium_mg", nullable = false)
    private Float calciumMg;

    @Column(name = "iron_mg", nullable = false)
    private Float ironMg;

    @Column(name = "potassium_mg", nullable = false)
    private Float potassiumMg;

	public Integer getIngredientId() {
		return ingredientId;
	}

	public void setIngredientId(Integer ingredientId) {
		this.ingredientId = ingredientId;
	}

	public NutritionResult getNutritionResult() {
		return nutritionResult;
	}

	public void setNutritionResult(NutritionResult nutritionResult) {
		this.nutritionResult = nutritionResult;
	}

	public String getNameOriginal() {
		return nameOriginal;
	}

	public void setNameOriginal(String nameOriginal) {
		this.nameOriginal = nameOriginal;
	}

	public String getNameEn() {
		return nameEn;
	}

	public void setNameEn(String nameEn) {
		this.nameEn = nameEn;
	}

	public Float getQuantityValue() {
		return quantityValue;
	}

	public void setQuantityValue(Float quantityValue) {
		this.quantityValue = quantityValue;
	}

	public String getUnitOriginal() {
		return unitOriginal;
	}

	public void setUnitOriginal(String unitOriginal) {
		this.unitOriginal = unitOriginal;
	}

	public String getUnitEn() {
		return unitEn;
	}

	public void setUnitEn(String unitEn) {
		this.unitEn = unitEn;
	}

	public Float getEstimatedWeightG() {
		return estimatedWeightG;
	}

	public void setEstimatedWeightG(Float estimatedWeightG) {
		this.estimatedWeightG = estimatedWeightG;
	}

	public Float getCalories() {
		return calories;
	}

	public void setCalories(Float calories) {
		this.calories = calories;
	}

	public Float getTotalFatG() {
		return totalFatG;
	}

	public void setTotalFatG(Float totalFatG) {
		this.totalFatG = totalFatG;
	}

	public Float getSaturatedFatG() {
		return saturatedFatG;
	}

	public void setSaturatedFatG(Float saturatedFatG) {
		this.saturatedFatG = saturatedFatG;
	}

	public Float getCholesterolMg() {
		return cholesterolMg;
	}

	public void setCholesterolMg(Float cholesterolMg) {
		this.cholesterolMg = cholesterolMg;
	}

	public Float getSodiumMg() {
		return sodiumMg;
	}

	public void setSodiumMg(Float sodiumMg) {
		this.sodiumMg = sodiumMg;
	}

	public Float getTotalCarbohydrateG() {
		return totalCarbohydrateG;
	}

	public void setTotalCarbohydrateG(Float totalCarbohydrateG) {
		this.totalCarbohydrateG = totalCarbohydrateG;
	}

	public Float getDietaryFiberG() {
		return dietaryFiberG;
	}

	public void setDietaryFiberG(Float dietaryFiberG) {
		this.dietaryFiberG = dietaryFiberG;
	}

	public Float getTotalSugarsG() {
		return totalSugarsG;
	}

	public void setTotalSugarsG(Float totalSugarsG) {
		this.totalSugarsG = totalSugarsG;
	}

	public Float getProteinG() {
		return proteinG;
	}

	public void setProteinG(Float proteinG) {
		this.proteinG = proteinG;
	}

	public Float getVitaminDMcg() {
		return vitaminDMcg;
	}

	public void setVitaminDMcg(Float vitaminDMcg) {
		this.vitaminDMcg = vitaminDMcg;
	}

	public Float getCalciumMg() {
		return calciumMg;
	}

	public void setCalciumMg(Float calciumMg) {
		this.calciumMg = calciumMg;
	}

	public Float getIronMg() {
		return ironMg;
	}

	public void setIronMg(Float ironMg) {
		this.ironMg = ironMg;
	}

	public Float getPotassiumMg() {
		return potassiumMg;
	}

	public void setPotassiumMg(Float potassiumMg) {
		this.potassiumMg = potassiumMg;
	}


}