package com.me.api.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.me.util.LogManager;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Optimized utility for memory-efficient JSON test case handling
 * Uses streaming and caching to improve performance with large test case collections
 */
public class TestCaseJsonUtil {
    
    private static final Logger LOGGER = LogManager.getLogger(TestCaseJsonUtil.class.getName(), LogManager.LOG_TYPE.FW);
    
    // In-memory index of test case IDs to file paths
    private static final Map<String, String> TEST_CASE_INDEX = new ConcurrentHashMap<>();
    
    // LRU cache for frequently accessed test cases (limited size)
    private static final int MAX_CACHE_SIZE = 1000; // Adjust based on memory constraints
    private static final Map<String, JsonObject> TEST_CASE_CACHE = new ConcurrentHashMap<>();
    
    /**
     * Load and index all test case IDs from the test cases directory
     * This builds an in-memory index without loading the full content
     */
    public static void buildTestCaseIndex() {
        TEST_CASE_INDEX.clear();
        
        try {
            String testCasesDir = System.getProperty("goat.testcases.path", "../../manual_cases");
            Path dirPath = Paths.get(testCasesDir);
            
            if (!Files.exists(dirPath)) {
                LOGGER.warning("Test cases directory does not exist: " + testCasesDir);
                return;
            }
            
            // Stream through all JSON files
            try (Stream<Path> paths = Files.list(dirPath)) {
                paths.filter(path -> path.toString().endsWith(".json"))
                     .forEach(path -> indexTestCasesInFile(path));
            }
            
            LOGGER.info("Finished building test case index. Total test cases: " + TEST_CASE_INDEX.size());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error building test case index", e);
        }
    }
    
    /**
     * Index test cases in a single file using streaming parser
     */
    private static void indexTestCasesInFile(Path filePath) {
        try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(filePath.toFile()), StandardCharsets.UTF_8))) {
            reader.beginObject(); // Start of JSON object
            
            // Read each top-level key (test case ID)
            while (reader.hasNext()) {
                String testCaseId = reader.nextName();
                TEST_CASE_INDEX.put(testCaseId, filePath.toString());
                
                // Skip over the value (we only need the keys for the index)
                reader.skipValue();
            }
            
            reader.endObject();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error indexing test cases in file: " + filePath, e);
        }
    }
    
    /**
     * Get a specific test case by ID using the index for fast retrieval
     */
    public static Optional<JsonObject> getTestCase(String testCaseId) {
        // Check the cache first
        if (TEST_CASE_CACHE.containsKey(testCaseId)) {
            LOGGER.info("Test case cache hit: " + testCaseId);
            return Optional.of(TEST_CASE_CACHE.get(testCaseId));
        } else {
            buildTestCaseIndex();
        }
//        // If index is empty, build it
//        if (TEST_CASE_INDEX.isEmpty()) {
//            buildTestCaseIndex();
//        }
        
        // Look up the file path in the index
        String filePath = TEST_CASE_INDEX.get(testCaseId);
        if (filePath == null) {
            LOGGER.info("Test case not found in index: " + testCaseId);
            return Optional.empty();
        }
        
        try {
            // Load only the specific test case from the file
            String fileContent = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
            JsonObject jsonObj = JsonParser.parseString(fileContent).getAsJsonObject();
            
            if (jsonObj.has(testCaseId)) {
                JsonElement testCaseElement = jsonObj.get(testCaseId);
                if (testCaseElement.isJsonObject()) {
                    JsonObject testCase = testCaseElement.getAsJsonObject();
                    
                    // Add to cache if not too large
                    if (TEST_CASE_CACHE.size() < MAX_CACHE_SIZE) {
                        TEST_CASE_CACHE.put(testCaseId, testCase);
                    } else if (!TEST_CASE_CACHE.isEmpty()) {
                        // Simple cache eviction - remove a random entry
                        // A more sophisticated LRU implementation could be used instead
                        String keyToRemove = TEST_CASE_CACHE.keySet().iterator().next();
                        TEST_CASE_CACHE.remove(keyToRemove);
                        TEST_CASE_CACHE.put(testCaseId, testCase);
                    }
                    
                    return Optional.of(testCase);
                }
            }
            
            return Optional.empty();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error loading test case: " + testCaseId, e);
            return Optional.empty();
        }
    }
    
    /**
     * Memory-efficient method to check if a test case exists without loading it
     */
    public static boolean testCaseExists(String testCaseId) {
        // If index is empty, build it
        if (TEST_CASE_INDEX.isEmpty()) {
            buildTestCaseIndex();
        }
        
        return TEST_CASE_INDEX.containsKey(testCaseId);
    }
    
    /**
     * Get all test case IDs without loading the content
     */
    public static Map<String, String> getAllTestCaseIds() {
        // If index is empty, build it
        if (TEST_CASE_INDEX.isEmpty()) {
            buildTestCaseIndex();
        }
        
        return new HashMap<>(TEST_CASE_INDEX);
    }
    
    /**
     * Clear the test case cache to free memory
     */
    public static void clearCache() {
        TEST_CASE_CACHE.clear();
        LOGGER.info("Test case cache cleared");
    }
    
    /**
     * Reload the test case index to reflect file system changes
     */
    public static void reloadIndex() {
        buildTestCaseIndex();
    }
}