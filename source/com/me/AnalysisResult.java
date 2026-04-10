package com.me;

/**
 * Class to hold test analysis results
 */
public class AnalysisResult {
    private int totalTests;
    private int passedTests;
    private int failedTests;
    private int skippedTests;
    private int warningTests;
    private int errorTests;
    private long totalExecutionTime;
    private double successRate;
    
    /**
     * Default constructor
     */
    public AnalysisResult() {
        this.totalTests = 0;
        this.passedTests = 0;
        this.failedTests = 0;
        this.skippedTests = 0;
        this.warningTests = 0;
        this.errorTests = 0;
        this.totalExecutionTime = 0;
        this.successRate = 0.0;
    }
    
    /**
     * Constructor with main statistics
     */
    public AnalysisResult(int totalTests, int passedTests, int failedTests, int skippedTests) {
        this.totalTests = totalTests;
        this.passedTests = passedTests;
        this.failedTests = failedTests;
        this.skippedTests = skippedTests;
        this.warningTests = 0;
        this.errorTests = 0;
        this.totalExecutionTime = 0;
        calculateSuccessRate();
    }
    
    /**
     * Calculate success rate based on current statistics
     */
    private void calculateSuccessRate() {
        if (totalTests > 0) {
            this.successRate = (double) passedTests / totalTests * 100;
        } else {
            this.successRate = 0;
        }
    }
    
    // Getters and setters
    public int getTotalTests() {
        return totalTests;
    }
    
    public void setTotalTests(int totalTests) {
        this.totalTests = totalTests;
        calculateSuccessRate();
    }
    
    public int getPassedTests() {
        return passedTests;
    }
    
    public void setPassedTests(int passedTests) {
        this.passedTests = passedTests;
        calculateSuccessRate();
    }
    
    public int getFailedTests() {
        return failedTests;
    }
    
    public void setFailedTests(int failedTests) {
        this.failedTests = failedTests;
    }
    
    public int getSkippedTests() {
        return skippedTests;
    }
    
    public void setSkippedTests(int skippedTests) {
        this.skippedTests = skippedTests;
    }
    
    public int getWarningTests() {
        return warningTests;
    }
    
    public void setWarningTests(int warningTests) {
        this.warningTests = warningTests;
    }
    
    public int getErrorTests() {
        return errorTests;
    }
    
    public void setErrorTests(int errorTests) {
        this.errorTests = errorTests;
    }
    
    public long getTotalExecutionTime() {
        return totalExecutionTime;
    }
    
    public void setTotalExecutionTime(long totalExecutionTime) {
        this.totalExecutionTime = totalExecutionTime;
    }
    
    public double getSuccessRate() {
        return successRate;
    }
    
    /**
     * Get a summary of the analysis results
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("Test Analysis Summary:\n"); //NO I18N
        sb.append("Total Tests: ").append(totalTests).append("\n");//NO I18N
        sb.append("Passed: ").append(passedTests).append("\n");//NO I18N
        sb.append("Failed: ").append(failedTests).append("\n");//NO I18N
        if (skippedTests > 0) {
            sb.append("Skipped: ").append(skippedTests).append("\n");//NO I18N
        }
        if (warningTests > 0) {
            sb.append("Warnings: ").append(warningTests).append("\n");//NO I18N
        }
        if (errorTests > 0) {
            sb.append("Errors: ").append(errorTests).append("\n");//NO I18N
        }
        sb.append("Success Rate: ").append(String.format("%.2f", successRate)).append("%\n");//NO I18N
        sb.append("Total Execution Time: ").append(formatExecutionTime(totalExecutionTime));//NO I18N
        
        return sb.toString();
    }
    
    /**
     * Format execution time in a human-readable way
     */
    private String formatExecutionTime(long executionTimeMs) {
        if (executionTimeMs < 1000) {
            return executionTimeMs + " ms";//NO I18N
        } else if (executionTimeMs < 60000) {
            return String.format("%.2f sec", executionTimeMs / 1000.0);//NO I18N
        } else {
            long minutes = executionTimeMs / 60000;
            long seconds = (executionTimeMs % 60000) / 1000;
            return minutes + " min " + seconds + " sec";//NO I18N
        }
    }
}
