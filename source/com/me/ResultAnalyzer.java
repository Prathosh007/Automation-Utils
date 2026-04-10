package com.me;

import java.util.List;
import java.util.logging.Logger;

import com.me.util.LogManager;

/**
 * Analyzes test results to provide statistics and insights
 */
public class ResultAnalyzer {
    private static final Logger LOGGER = LogManager.getLogger(ResultAnalyzer.class, LogManager.LOG_TYPE.FW);
    
    /**
     * Analyze test results and generate statistics
     * 
     * @param results List of test results
     * @return Analysis result with statistics
     */
    public AnalysisResult analyze(List<TestResult> results) {
        LOGGER.info("Analyzing test results"); //No I18N
        
        int total = results.size();
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        int warnings = 0;
        int errors = 0;
        long totalExecutionTime = 0;
        
        for (TestResult result : results) {
            // Count by status
            switch (result.getStatus()) {
                case TestResult.STATUS_PASSED:
                    passed++;
                    break;
                case TestResult.STATUS_FAILED:
                    failed++;
                    break;
                case TestResult.STATUS_SKIPPED:
                    skipped++;
                    break;
                case TestResult.STATUS_WARNING:
                    warnings++;
                    break;
                case TestResult.STATUS_ERROR:
                    errors++;
                    break;
                default:
                    LOGGER.warning("Unknown status: " + result.getStatus()); //No I18N
                    break;
            }
            
            // Sum execution time
            totalExecutionTime += result.getExecutionTime();
        }
        
        // Create analysis result
        AnalysisResult analysisResult = new AnalysisResult();
        analysisResult.setTotalTests(total);
        analysisResult.setPassedTests(passed);
        analysisResult.setFailedTests(failed);
        analysisResult.setSkippedTests(skipped);
        analysisResult.setWarningTests(warnings);
        analysisResult.setErrorTests(errors);
        analysisResult.setTotalExecutionTime(totalExecutionTime);
        
        LOGGER.info("Analysis complete: " + passed + "/" + total + " tests passed"); //No I18N
        
        return analysisResult;
    }
}
