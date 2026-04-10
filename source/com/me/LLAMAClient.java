package com.me;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

// Add Gson imports
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.stream.JsonReader;

import com.me.util.LogManager;
import com.adventnet.mfw.ConsoleOut;

/**
 * Client for making requests to LLAMA language model service
 */
public class LLAMAClient {
    private static final Logger LOGGER = LogManager.getLogger(LLAMAClient.class, LogManager.LOG_TYPE.FW);
    public static final String CONTENT_TYPE = "application/json"; //NO I18N
    
    private String apiEndpoint;
    private String modelName;
    private int maxTokens;
    
    /**
     * Constructor for LLAMAClient
     * 
     * @param apiEndpoint The API endpoint URL for the LLAMA service
     * @param modelName The model name to use
     * @param maxTokens Maximum number of tokens to generate
     */
    public LLAMAClient(String apiEndpoint, String modelName, int maxTokens) {
        this.apiEndpoint = apiEndpoint;
        this.modelName = modelName;
        this.maxTokens = maxTokens;
        
        LOGGER.info("Initialized LLAMA client with endpoint: " + apiEndpoint + ", model: " + modelName); //NO I18N
    }

    // Model configuration
    private static final String DEFAULT_MODEL = "goat-llm";  // Use our custom model //NO I18N
    private static final String FALLBACK_MODEL = "llama3.1";  // Fallback to base model if custom not available //NO I18N

    public static void sendJsonToLLAMA(String jsonFilePath, String rawOutputFilePath, String extractedOutputFilePath) throws IOException {
        String url = "http://localhost:11434/api/generate"; //NO I18N
        String jsonContent = new String(Files.readAllBytes(Paths.get(jsonFilePath)));

        // Create the JSON payload for LLAMA
        JsonObject payload = new JsonObject();
        
        // Try to use the custom model first, if it fails we'll fall back to the base model
        String modelName = checkModelAvailability(DEFAULT_MODEL) ? DEFAULT_MODEL : FALLBACK_MODEL;
        payload.addProperty("model", modelName); //NO I18N
        
        LOGGER.info("Using model: " + modelName); //NO I18N
        
        // If using custom model (goat-llm), we don't need the detailed prompt
        // as the instructions are built into the model
        if (modelName.equals(DEFAULT_MODEL)) {
            // Simple prompt for custom model
            payload.addProperty("prompt", jsonContent); //NO I18N
            // Use model's built-in parameters
        } else {
            // Detailed prompt for base model - using the existing prompt
            StringBuilder prompt = new StringBuilder();
            prompt.append("Extract relevant details from each test case in the following JSON array. The input JSON array contains multiple objects with these keys: ") //NO I18N
              .append("- `id` (Test Case ID) ") //NO I18N
              .append("- `testcase` (Test Case Description) ") //NO I18N
              .append("- `steps` (Test Steps) ") //NO I18N
              .append("- `expectedResult` (Expected Outcome) ") //NO I18N
              
              .append("### **Extraction Rules:** ") //NO I18N
              .append("For each test case in the array,understand the context of each cases and extract and return: ") //NO I18N
              .append("- `testcase_id`: Use the value of `id`. ") //NO I18N
              .append("- `file_path`: Extract from `testcase` or `steps`, identifying the file location,there should no \"Folder\" text in file_path. ") //NO I18N
              .append("- `filename`: Extract from `testcase` or `steps`, identifying the file name. ") //NO I18N
              .append("- `operation`: Determine based on testcase context: ") //NO I18N
              .append("- If checking for file presence/absence → `\"check_presence\"` or `\"verify_absence\"` ") //NO I18N
              .append("- If checking for specific value presence or not in particular file → `\"value_should_be_present\"` or `\"value_should_be_removed\"` ") //NO I18N
              .append("- `expected_result`: Extract from `expectedResult`. ") //NO I18N
              .append("- `value`: Extract only relevant content for value based operations. If not applicable, return an empty string (`\"\"`). ") //NO I18N
              
              .append("### **Response Format:** ") //NO I18N
              .append("- Return a **strict JSON object** with a `\"test_cases\"` array containing extracted details. ") //NO I18N
              .append("- **No additional text, markdown, or explanations.** ") //NO I18N
              .append("- **No line breaks (`\\n`) inside JSON values.** ") //NO I18N
              .append("- The response must start with `{ \"test_cases\": [` and end with `] }`. ") //NO I18N
              
              .append("### **Input JSON Array:**") //NO I18N
              .append(jsonContent);
            
            payload.addProperty("prompt", prompt.toString()); //NO I18N
            
            // Set model parameters for base model
            payload.addProperty("temperature", 0.1); //NO I18N
            payload.addProperty("top_p", 0.9); //NO I18N
            payload.addProperty("top_k", 40); //NO I18N
        }
        
        // Always set stream to false for complete response
        payload.addProperty("stream", false); //NO I18N

        // Connect to the LLAMA API
        LOGGER.info("Connecting to LLAMA API: " + url); //NO I18N
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST"); //NO I18N
        connection.setRequestProperty("Content-Type", "application/json; utf-8"); //NO I18N
        connection.setDoOutput(true);
        connection.setConnectTimeout(30000);  // 30 seconds connection timeout
        connection.setReadTimeout(600000);    // 5 minutes read timeout for large responses

        // Send the payload
        LOGGER.info("Sending request to LLAMA..."); //NO I18N
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = payload.toString().getBytes("utf-8"); //NO I18N
            os.write(input, 0, input.length);
        }

        // Check if the response is successful
        int responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            LOGGER.severe("HTTP error code: " + responseCode); //NO I18N
            throw new IOException("HTTP error code: " + responseCode); //NO I18N
        }

        // Read the response
        LOGGER.info("Reading LLAMA response..."); //NO I18N
        String responseString;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"))) { //NO I18N
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
            response.append(responseLine.trim());
            }
            responseString = response.toString();
        }

        // Write raw response to file
        LOGGER.info("Writing raw response to file: " + rawOutputFilePath); //NO I18N
        try (FileWriter rawWriter = new FileWriter(rawOutputFilePath)) {
            rawWriter.write(responseString);
        }

        // Parse and process the response
        JsonObject jsonResponse = JsonParser.parseString(responseString).getAsJsonObject();
        
        if (!jsonResponse.has("response")) { //NO I18N
            LOGGER.severe("Invalid response format: 'response' field missing"); //NO I18N
            throw new IOException("Invalid response format: 'response' field missing"); //NO I18N
        }
        
        String llmResponse = jsonResponse.get("response").getAsString(); //NO I18N
        
        // Extract clean JSON from the response
        String extractedJson = extractJsonFromText(llmResponse);
        
        if (extractedJson == null || extractedJson.isEmpty()) {
            LOGGER.severe("Failed to extract valid JSON from LLAMA response"); //NO I18N
            throw new IOException("Failed to extract valid JSON from LLAMA response"); //NO I18N
        }
        
        // Format the extracted JSON for output
        try {
            JsonObject responseObject;
            
            // Check if the response is in test_cases format or direct object format
            if (extractedJson.contains("\"test_cases\":")) { //NO I18N
                // Extract from test_cases array
                JsonObject testCasesObj = JsonParser.parseString(extractedJson).getAsJsonObject();
                JsonArray testCasesArray = testCasesObj.getAsJsonArray("test_cases"); //NO I18N
                responseObject = new JsonObject();
                
                // Convert array to object with test IDs as keys
                for (JsonElement element : testCasesArray) {
                    JsonObject testCase = element.getAsJsonObject();
                    String testcaseId = testCase.get("testcase_id").getAsString(); //NO I18N
                    responseObject.add(testcaseId, testCase);
                }
            } else {
                // Direct object format (already in correct format)
                responseObject = JsonParser.parseString(extractedJson).getAsJsonObject();
            }
            
            // Pretty print for better readability
            String formattedJson = new com.google.gson.GsonBuilder().setPrettyPrinting().create()
                    .toJson(responseObject);
            
            // Write formatted response to output file
            try (FileWriter writer = new FileWriter(extractedOutputFilePath)) {
                writer.write(formattedJson);
            }
            
            LOGGER.info("Extracted JSON saved to: " + extractedOutputFilePath); //NO I18N
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error formatting JSON", e); //NO I18N
            throw new IOException("Error formatting JSON response", e); //NO I18N
        }
    }
    
    /**
     * Check if a model is available in OLLAMA
     * @param modelName The model to check
     * @return true if the model exists and is available
     */
    private static boolean checkModelAvailability(String modelName) {
        try {
            LOGGER.info("Checking if model exists: " + modelName); //NO I18N
            URL url = new URL("http://localhost:11434/api/tags"); //NO I18N
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET"); //NO I18N
            
            if (conn.getResponseCode() != 200) {
                LOGGER.warning("Failed to check model availability, status: " + conn.getResponseCode()); //NO I18N
                return false;
            }
            
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            
            String responseJson = response.toString();
            LOGGER.fine("Received model list: " + responseJson); //NO I18N
            
            // Look for model name with or without :latest suffix
            boolean modelExists = responseJson.contains("\"name\":\"" + modelName + "\"") || //NO I18N
                                 responseJson.contains("\"name\":\"" + modelName + ":latest\"") || //NO I18N
                                 responseJson.contains("\"model\":\"" + modelName + "\"") || //NO I18N
                                 responseJson.contains("\"model\":\"" + modelName + ":latest\""); //NO I18N
            
            LOGGER.info("Model " + modelName + (modelExists ? " found" : " not found") + " in available models"); //NO I18N
            return modelExists;
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error checking model availability", e); //NO I18N
            return false;
        }
    }
    
    /**
     * Extract valid JSON from text that may contain non-JSON content
     */
    private static String extractJsonFromText(String text) {
        // First, look for JSON between code blocks
        int startJson = text.indexOf("```json"); //NO I18N
        if (startJson != -1) {
            int endJson = text.indexOf("```", startJson + 6); //NO I18N
            if (endJson != -1) {
                // Extract JSON between the code blocks
                return text.substring(startJson + 7, endJson).trim();
            }
        }
        
        // If JSON code blocks not found, look for standalone JSON
        startJson = text.indexOf('{');
        int startArray = text.indexOf('[');
        
        // Determine which comes first, { or [
        int firstJsonChar;
        if (startJson == -1) {
            firstJsonChar = startArray;
        } else if (startArray == -1) {
            firstJsonChar = startJson;
        } else {
            firstJsonChar = Math.min(startJson, startArray);
        }
        
        if (firstJsonChar == -1) {
            // No JSON found
            return null;
        }
        
        // Find matching closing bracket/brace
        return findMatchingJson(text, firstJsonChar);
    }
    
    /**
     * Find a complete JSON object or array starting from the given index
     */
    private static String findMatchingJson(String text, int start) {
        char openChar = text.charAt(start);
        char closeChar;
        
        if (openChar == '{') {
            closeChar = '}';
        } else if (openChar == '[') {
            closeChar = ']';
        } else {
            return null;
        }
        
        int level = 1;
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = start + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                continue;
            }
            
            if (inString) {
                continue;
            }
            
            if (c == openChar) {
                level++;
            } else if (c == closeChar) {
                level--;
                if (level == 0) {
                    // Found matching close bracket/brace
                    return text.substring(start, i + 1);
                }
            }
        }
        
        // No matching close found
        return null;
    }

    /**
     * Send a prompt to the LLAMA model and get the response
     * 
     * @param prompt The text prompt to send
     * @return The model's response text
     * @throws IOException If an error occurs during the request
     */
    public String sendPrompt(String prompt) throws IOException {
        LOGGER.info("Sending prompt to LLAMA model"); //NO I18N
        
        // Create connection
        URL url = new URL(apiEndpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST"); //NO I18N
        connection.setRequestProperty("Content-Type", CONTENT_TYPE); //NO I18N
        connection.setRequestProperty("Accept", CONTENT_TYPE); //NO I18N
        connection.setDoOutput(true);
        connection.setConnectTimeout(30000); // 30 seconds
        connection.setReadTimeout(60000);    // 60 seconds
        
        // Create JSON request body
        String jsonInputString = createRequestJson(prompt);
        
        // Send request
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        // Check response code
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Unexpected response code: " + responseCode); //NO I18N
        }
        
        // Read response
        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        }
        
        // Parse the response to extract the generated text
        String responseText = parseResponse(response.toString());
        
        LOGGER.info("Received response from LLAMA model"); //NO I18N
        return responseText;
    }
    
    /**
     * Create the JSON request string
     * 
     * @param prompt The prompt text
     * @return JSON string for the request body
     */
    private String createRequestJson(String prompt) {
        try {
            // Use Gson if available
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", modelName); //NO I18N
            requestBody.addProperty("prompt", prompt); //NO I18N
            requestBody.addProperty("stream", false); //NO I18N
            requestBody.addProperty("max_tokens", maxTokens); //NO I18N
            return requestBody.toString();
        } catch (NoClassDefFoundError e) {
            // Fallback to manual JSON if Gson is unavailable
            return String.format(
                "{\"model\":\"%s\",\"prompt\":\"%s\",\"stream\":false,\"max_tokens\":%d}", //NO I18N
                modelName, 
                prompt.replace("\"", "\\\""), //NO I18N
                maxTokens
            );
        }
    }
    
    /**
     * Parse the response JSON to extract the generated text
     * 
     * @param jsonResponse The JSON response from the API
     * @return The generated text
     */
    private String parseResponse(String jsonResponse) {
        try {
            // Use Gson to parse the response
            JsonObject responseObj = JsonParser.parseString(jsonResponse).getAsJsonObject();
            if (responseObj.has("response")) { //NO I18N
                return responseObj.get("response").getAsString(); //NO I18N
            } else if (responseObj.has("choices") && responseObj.getAsJsonArray("choices").size() > 0) { //NO I18N
                return responseObj.getAsJsonArray("choices").get(0) //NO I18N
                        .getAsJsonObject().get("text").getAsString(); //NO I18N
            } else {
                LOGGER.warning("Unexpected response format from LLAMA model"); //NO I18N
                return jsonResponse; // Return raw response if we can't parse it properly
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error parsing LLAMA response", e); //NO I18N
            return jsonResponse; // Return raw response on error
        }
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            ConsoleOut.println("Usage: java LLAMAClient <jsonFilePath> <rawOutputFilePath> <extractedOutputFilePath>"); //NO I18N
            return;
        }

        String jsonFilePath = args[0];
        String rawOutputFilePath = args[1];
        String extractedOutputFilePath = args[2];

        try {
            sendJsonToLLAMA(jsonFilePath, rawOutputFilePath, extractedOutputFilePath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error processing LLAMA request", e); //NO I18N
        }
    }
}
