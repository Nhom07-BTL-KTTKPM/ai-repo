package iuh.fit.aiservice.recommendation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.recommendation")
public class RecommendationProperties {

    private int topK;
    private int maxCandidates;
    private double minScore;
    private boolean replaceExisting;
    private int viewLogLimit;
    private double weightView;
    private double weightClick;
    private double weightAddToCart;
    private double weightPurchase;
    private double recencyHalfLifeDays;
    private double aiBoostCategory;
    private double aiBoostBrand;
    private double aiBoostConcern;
    private boolean aiReasonEnabled;
    private boolean aiPreferenceEnabled;
    private String cleanupCron;
    private int cleanupKeepTop;
    private int cleanupRetentionDays;

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getMaxCandidates() {
        return maxCandidates;
    }

    public void setMaxCandidates(int maxCandidates) {
        this.maxCandidates = maxCandidates;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    public boolean isReplaceExisting() {
        return replaceExisting;
    }

    public void setReplaceExisting(boolean replaceExisting) {
        this.replaceExisting = replaceExisting;
    }

    public int getViewLogLimit() {
        return viewLogLimit;
    }

    public void setViewLogLimit(int viewLogLimit) {
        this.viewLogLimit = viewLogLimit;
    }

    public double getWeightView() {
        return weightView;
    }

    public void setWeightView(double weightView) {
        this.weightView = weightView;
    }

    public double getWeightClick() {
        return weightClick;
    }

    public void setWeightClick(double weightClick) {
        this.weightClick = weightClick;
    }

    public double getWeightAddToCart() {
        return weightAddToCart;
    }

    public void setWeightAddToCart(double weightAddToCart) {
        this.weightAddToCart = weightAddToCart;
    }

    public double getWeightPurchase() {
        return weightPurchase;
    }

    public void setWeightPurchase(double weightPurchase) {
        this.weightPurchase = weightPurchase;
    }

    public double getRecencyHalfLifeDays() {
        return recencyHalfLifeDays;
    }

    public void setRecencyHalfLifeDays(double recencyHalfLifeDays) {
        this.recencyHalfLifeDays = recencyHalfLifeDays;
    }

    public double getAiBoostCategory() {
        return aiBoostCategory;
    }

    public void setAiBoostCategory(double aiBoostCategory) {
        this.aiBoostCategory = aiBoostCategory;
    }

    public double getAiBoostBrand() {
        return aiBoostBrand;
    }

    public void setAiBoostBrand(double aiBoostBrand) {
        this.aiBoostBrand = aiBoostBrand;
    }

    public double getAiBoostConcern() {
        return aiBoostConcern;
    }

    public void setAiBoostConcern(double aiBoostConcern) {
        this.aiBoostConcern = aiBoostConcern;
    }

    public boolean isAiReasonEnabled() {
        return aiReasonEnabled;
    }

    public void setAiReasonEnabled(boolean aiReasonEnabled) {
        this.aiReasonEnabled = aiReasonEnabled;
    }

    public boolean isAiPreferenceEnabled() {
        return aiPreferenceEnabled;
    }

    public void setAiPreferenceEnabled(boolean aiPreferenceEnabled) {
        this.aiPreferenceEnabled = aiPreferenceEnabled;
    }

    public String getCleanupCron() {
        return cleanupCron;
    }

    public void setCleanupCron(String cleanupCron) {
        this.cleanupCron = cleanupCron;
    }

    public int getCleanupKeepTop() {
        return cleanupKeepTop;
    }

    public void setCleanupKeepTop(int cleanupKeepTop) {
        this.cleanupKeepTop = cleanupKeepTop;
    }

    public int getCleanupRetentionDays() {
        return cleanupRetentionDays;
    }

    public void setCleanupRetentionDays(int cleanupRetentionDays) {
        this.cleanupRetentionDays = cleanupRetentionDays;
    }
}
