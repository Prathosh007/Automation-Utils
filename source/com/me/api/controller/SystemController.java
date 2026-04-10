package com.me.api.controller;

import com.me.api.bridge.GoatFrameworkBridge;
import com.me.api.model.GoatApiResponse;
import com.me.api.util.TestCaseJsonUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller to provide system maintenance and metadata operations
 * Especially useful for large test case collections
 */
@RestController
@RequestMapping("/system")
@Tag(name = "System", description = "System operations")
public class SystemController {
    
    private static final Logger LOGGER = Logger.getLogger(SystemController.class.getName());
    
    @Autowired
    private GoatFrameworkBridge frameworkBridge;

    @Value("${application.version}")
    private String appVersion;
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "Health Check", description = "Check if the API server is up and running")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "System is healthy",
            content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = GoatApiResponse.class)))
    })
    public ResponseEntity<?> healthCheck() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(new GoatApiResponse<>(true, "System is healthy", status));
    }
    
    /**
     * System information endpoint
     */
    @GetMapping("/info")
    @Operation(summary = "System Information", description = "Get detailed information about the G.O.A.T API server")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "System information retrieved successfully",
            content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = GoatApiResponse.class)))
    })
    public ResponseEntity<?> systemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "G.O.A.T API");
        info.put("version", appVersion);
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("osName", System.getProperty("os.name"));
        info.put("uptime", ManagementFactory.getRuntimeMXBean().getUptime());
        
        return ResponseEntity.ok(new GoatApiResponse<>(true, "System information retrieved", info));
    }

    /**
     * Get metadata about the test cases in the system
     */
    @GetMapping("/metadata")
    public ResponseEntity<GoatApiResponse<Map<String, Object>>> getSystemMetadata() {
        try {
            LOGGER.info("Getting system metadata");
            
            Map<String, Object> metadata = new HashMap<>();
            
            // Get test case count
            List<String> testCaseIds = frameworkBridge.getTestCaseIds();
            metadata.put("totalTestCases", testCaseIds.size());
            
            // Get file information
            Map<String, String> testCaseMap = TestCaseJsonUtil.getAllTestCaseIds();
            
            // Count how many test cases per file
            Map<String, Integer> fileStats = new HashMap<>();
            for (String filePath : testCaseMap.values()) {
                fileStats.put(filePath, fileStats.getOrDefault(filePath, 0) + 1);
            }
            metadata.put("fileStats", fileStats);
            
            // Memory usage information
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            metadata.put("memoryUsage", usedMemory / (1024 * 1024) + " MB");
            metadata.put("maxMemory", runtime.maxMemory() / (1024 * 1024) + " MB");
            
            return ResponseEntity.ok(new GoatApiResponse<>(true, "System metadata retrieved", metadata));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting system metadata", e);
            return ResponseEntity.ok(
                new GoatApiResponse<>(false, "Error getting system metadata: " + e.getMessage(), null));
        }
    }
    
    /**
     * Rebuild the test case index (useful when new test cases are added manually)
     */
    @PostMapping("/rebuild-index")
    public ResponseEntity<GoatApiResponse<Map<String, Object>>> rebuildIndex() {
        try {
            LOGGER.info("Rebuilding test case index");
            
            // Clear cache first to free memory
            TestCaseJsonUtil.clearCache();
            
            // Rebuild the index
            frameworkBridge.reloadTestCaseIndex();
            
            // Get updated stats
            List<String> testCaseIds = frameworkBridge.getTestCaseIds();
            Map<String, Object> result = new HashMap<>();
            result.put("totalTestCases", testCaseIds.size());
            result.put("status", "Index rebuilt successfully");
            
            return ResponseEntity.ok(new GoatApiResponse<>(true, "Test case index rebuilt", result));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error rebuilding test case index", e);
            return ResponseEntity.ok(
                new GoatApiResponse<>(false, "Error rebuilding test case index: " + e.getMessage(), null));
        }
    }
    
    /**
     * Clear memory caches to free up system resources
     */
    @PostMapping("/clear-cache")
    public ResponseEntity<GoatApiResponse<Map<String, Object>>> clearCache() {
        try {
            LOGGER.info("Clearing test case cache");
            
            // Clear the test case cache
            TestCaseJsonUtil.clearCache();
            
            // Memory usage information after clearing
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", "Cache cleared successfully");
            result.put("memoryUsage", usedMemory / (1024 * 1024) + " MB");
            
            return ResponseEntity.ok(new GoatApiResponse<>(true, "Test case cache cleared", result));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error clearing test case cache", e);
            return ResponseEntity.ok(
                new GoatApiResponse<>(false, "Error clearing test case cache: " + e.getMessage(), null));
        }
    }
}
