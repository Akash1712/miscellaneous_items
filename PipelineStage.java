package com.yourcompany.logging;

/**
 * Pipeline stage constants for consistent logging
 */
public enum PipelineStage {
    CONSUMED("CONSUMED"),
    VALIDATION("VALIDATION"),
    MARKET_CLASSIFICATION("MARKET_CLASSIFICATION"),
    DB_OPERATION("DB_OPERATION"),
    PUBLISHED("PUBLISHED"),
    RETRY_A("RETRY_A"),
    RETRY_B("RETRY_B"),
    FAILED("FAILED");
    
    private final String stage;
    
    PipelineStage(String stage) {
        this.stage = stage;
    }
    
    public String getStage() {
        return stage;
    }
    
    @Override
    public String toString() {
        return stage;
    }
}
