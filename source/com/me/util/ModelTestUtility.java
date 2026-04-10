package com.me.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.adventnet.mfw.ConsoleOut;

/**
 * Utility for testing LLM models
 */
public class ModelTestUtility {
    private static final Logger LOGGER = LogManager.getLogger(ModelTestUtility.class, LogManager.LOG_TYPE.FW);
    
    /**
     * Test if Ollama is running and the model is available
     * 
     * @param model The model name to check
     * @return true if the model is available, false otherwise
     */
    public static boolean isModelAvailable(String model) {
        try {
            ConsoleOut.println("Checking if model is available: " + model); //NO I18N
            
            // Build URL for Ollama API
            URL url = new URL("http://localhost:11434/api/tags"); //NO I18N
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET"); //NO I18N
            
            // Get response code
            int responseCode = connection.getResponseCode();
            
            if (responseCode == 200) {
                // Read response
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                // Check if model name is in the response
                String responseBody = response.toString();
                if (responseBody.contains("\"name\":\"" + model + "\"")) { //NO I18N
                    ConsoleOut.println("Model " + model + " is available"); //NO I18N
                    return true;
                } else {
                    ConsoleOut.println("Model " + model + " is not available"); //NO I18N
                    return false;
                }
            } else {
                ConsoleOut.println("Failed to connect to Ollama server. Response code: " + responseCode); //NO I18N
                return false;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error checking model availability", e); //NO I18N
            ConsoleOut.println("Error checking model availability: " + e.getMessage()); //NO I18N
            return false;
        }
    }
    
    /**
     * Send a test prompt to the model
     * 
     * @param model The model name
     * @param prompt The prompt to send
     * @return The model's response or error message
     */
    public static String testModelResponse(String model, String prompt) {
        try {
            ConsoleOut.println("Sending test prompt to model: " + model); //NO I18N
            
            // Build URL for Ollama API
            URL url = new URL("http://localhost:11434/api/generate"); //NO I18N
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST"); //NO I18N
            connection.setRequestProperty("Content-Type", "application/json"); //NO I18N
            connection.setDoOutput(true);
            
            // Create request body
            String requestBody = "{\"model\":\"" + model + "\",\"prompt\":\"" + //NO I18N
                                escapeJson(prompt) + "\",\"stream\":false}"; //NO I18N
            
            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Get response code
            int responseCode = connection.getResponseCode();
            
            if (responseCode == 200) {
                // Read response
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                // Extract response text
                String responseBody = response.toString();
                if (responseBody.contains("\"response\":")) { //NO I18N
                    int startIndex = responseBody.indexOf("\"response\":\"") + 12; //NO I18N
                    int endIndex = responseBody.indexOf("\"", startIndex); //NO I18N
                    if (startIndex >= 0 && endIndex >= 0) {
                        return unescapeJson(responseBody.substring(startIndex, endIndex));
                    }
                }
                
                return responseBody;
            } else {
                // Read error response
                StringBuilder errorResponse = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                }
                
                return "Error: HTTP " + responseCode + "\n" + errorResponse.toString(); //NO I18N
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error testing model response", e); //NO I18N
            return "Exception: " + e.getMessage(); //NO I18N
        }
    }
    
    /**
     * Load prompt from file
     * 
     * @param filePath Path to the prompt file
     * @return The prompt text or error message
     */
    public static String loadPromptFromFile(String filePath) {
        try {
            if (filePath == null || filePath.isEmpty()) {
                return "Error: No file path provided"; //NO I18N
            }
            
            File file = new File(filePath);
            if (!file.exists()) {
                return "Error: File not found: " + filePath; //NO I18N
            }
            
            return new String(Files.readAllBytes(Paths.get(filePath)));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error loading prompt from file", e); //NO I18N
            return "Error loading file: " + e.getMessage(); //NO I18N
        }
    }
    
    /**
     * Escape special characters for JSON
     */
    private static String escapeJson(String input) {
        if (input == null) {
            return ""; //NO I18N
        }
        
        return input.replace("\\", "\\\\") //NO I18N
                   .replace("\"", "\\\"") //NO I18N
                   .replace("\b", "\\b") //NO I18N
                   .replace("\f", "\\f") //NO I18N
                   .replace("\n", "\\n") //NO I18N
                   .replace("\r", "\\r") //NO I18N
                   .replace("\t", "\\t"); //NO I18N
    }
    
    /**
     * Unescape JSON escaped characters
     */
    private static String unescapeJson(String input) {
        if (input == null) {
            return ""; //NO I18N
        }
        
        return input.replace("\\\\", "\\") //NO I18N
                   .replace("\\\"", "\"") //NO I18N
                   .replace("\\b", "\b") //NO I18N
                   .replace("\\f", "\f") //NO I18N
                   .replace("\\n", "\n") //NO I18N
                   .replace("\\r", "\r") //NO I18N
                   .replace("\\t", "\t"); //NO I18N
    }
    
    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            ConsoleOut.println("Usage: ModelTestUtility <command> [arguments]"); //NO I18N
            ConsoleOut.println("Commands:"); //NO I18N
            ConsoleOut.println("  check <model>     - Check if model is available"); //NO I18N
            ConsoleOut.println("  test <model> <prompt> - Test model with prompt"); //NO I18N
            ConsoleOut.println("  file <model> <file>   - Test model with prompt from file"); //NO I18N
            return;
        }
        
        String command = args[0];
        
        if ("check".equals(command)) { //NO I18N
            if (args.length < 2) {
                ConsoleOut.println("Please provide a model name to check"); //NO I18N
                return;
            }
            boolean available = isModelAvailable(args[1]);
            ConsoleOut.println("Model available: " + available); //NO I18N
        } else if ("test".equals(command)) { //NO I18N
            if (args.length < 3) {
                ConsoleOut.println("Please provide a model name and prompt"); //NO I18N
                return;
            }
            String response = testModelResponse(args[1], args[2]);
            ConsoleOut.println("\nModel Response:\n-------------\n" + response); //NO I18N
        } else if ("file".equals(command)) { //NO I18N
            if (args.length < 3) {
                ConsoleOut.println("Please provide a model name and file path"); //NO I18N
                return;
            }
            String prompt = loadPromptFromFile(args[2]);
            if (prompt.startsWith("Error:")) { //NO I18N
                ConsoleOut.println(prompt);
                return;
            }
            ConsoleOut.println("Loaded prompt from file (" + prompt.length() + " characters)"); //NO I18N
            String response = testModelResponse(args[1], prompt);
            ConsoleOut.println("\nModel Response:\n-------------\n" + response); //NO I18N
        } else {
            ConsoleOut.println("Unknown command: " + command); //NO I18N
        }
    }
}
