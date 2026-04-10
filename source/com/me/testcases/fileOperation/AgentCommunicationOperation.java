package com.me.testcases;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.me.Operation;
import com.me.ResolveOperationParameters;
import com.me.util.CommonUtill;
import com.me.util.LogManager;
import org.json.JSONArray;
import org.json.JSONObject;

import static com.me.ResolveOperationParameters.resolveVariableReferences;
import static com.me.util.CommonUtill.resolveHome;
import static com.me.util.GOATCommonConstants.Communication_Util_EXE;

/**
 * Handler for communication operations using CommunicationAutomation.exe
 * Parameters are read directly from the operation's parameters and passed as JSON to the exe.
 */
public class AgentCommunicationOperation {
    private static final Logger LOGGER = LogManager.getLogger(
            AgentCommunicationOperation.class, LogManager.LOG_TYPE.FW);

    public static boolean executeOperation(Operation op) {
        if (op == null) {
            LOGGER.warning("Operation is null");
            return false;
        }

        StringBuilder remarkBuilder = new StringBuilder();
        Path tempJsonFile = null;

        try {
            // Build a JSON object from the operation's direct parameters
            JSONObject communicationOp = new JSONObject();
            Map<String, String> params = op.getParameters();

            if (params == null || params.isEmpty()) {
                LOGGER.warning("No parameters found for communication operation");
                return false;
            }

            // Copy all parameters except internal ones (note, remarks, etc.) into the communication operation JSON
            // Preserve original JSON types (int, boolean) instead of storing everything as strings
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String key = entry.getKey();
                // Skip non-communication internal parameters
                if ("note".equals(key) || "remarks".equals(key)) {
                    continue;
                }
                String value = entry.getValue();
                communicationOp.put(key, convertToOriginalType(value));
            }

            LOGGER.info("Constructed communication operation JSON (before variable resolution): " + communicationOp.toString(2));

            // Wrap in a JSON array
            JSONArray communicationOperationsArray = new JSONArray();
            communicationOperationsArray.put(communicationOp);

            // Resolve variables in communication operations
            communicationOperationsArray = resolveCommunicationOperationVariables(communicationOperationsArray);

            LOGGER.info("Communication operations JSON (after variable resolution): " + communicationOperationsArray.toString(2));

            // Write JSON array to a temp file
            tempJsonFile = Files.createTempFile("communication_ops_", ".json");
            try (BufferedWriter writer = Files.newBufferedWriter(tempJsonFile)) {
                writer.write(communicationOperationsArray.toString(2)); // pretty print
            }

            LOGGER.info("Temp JSON file written to: " + tempJsonFile.toAbsolutePath().toString());

            // Run CommunicationAutomation EXE with temp JSON file path
            ProcessBuilder builder = new ProcessBuilder(
                    Communication_Util_EXE,
                    tempJsonFile.toAbsolutePath().toString()
            );
            LOGGER.info("Executing command: " + Communication_Util_EXE + " " + tempJsonFile.toAbsolutePath().toString());

            File communicationLogDir = new File(new File(Communication_Util_EXE).getParentFile(), "Logs" + File.separator + "Communication");
            if (!communicationLogDir.exists()) {
                communicationLogDir.mkdirs();
            }
            builder.directory(communicationLogDir);

            builder.redirectErrorStream(true);

            LOGGER.info("Starting Communication operation process...");
            Process process = builder.start();

            // Capture output
            String output = captureOutput(process.getInputStream());
            int exitCode = process.waitFor();

            LOGGER.info("Exit code: " + exitCode);
            remarkBuilder.append(output).append("\n");

            op.setRemarks(remarkBuilder.toString());

            // Handle note storage - save only extracted data value for all operations
            if (op.hasNote()) {
                String extractedValue = extractDataValue(output);
                LOGGER.info("Extracted data value: " + extractedValue);
                DataBaseOperationHandler.saveNote(op, extractedValue);
                LOGGER.info("Extracted value stored in note successfully");
            } else {
                LOGGER.warning("This action is not supported for Note");
            }

            return exitCode == 0;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Communication operation failed", e);
            remarkBuilder.append("\nError: ").append(e.getMessage());
            op.setRemarks(remarkBuilder.toString());
            return false;

        } finally {
            // Cleanup temp file
            if (tempJsonFile != null) {
                try {
                    Files.deleteIfExists(tempJsonFile);
                } catch (IOException ignore) {}
            }
        }
    }

    /**
     * Extract the data value from CommunicationAutomation.exe output
     *
     * Expected output format:
     * Operation_1|PASSED|String value read successfully|Data:someValue
     * SUMMARY|Total:1|Passed:1|Failed:0
     *
     * Extracts: "someValue"
     */
    private static String extractDataValue(String output) {
        if (output == null || output.isEmpty()) {
            LOGGER.warning("Output is empty, returning empty string");
            return "";
        }

        String[] lines = output.split("\n");

        for (String line : lines) {
            LOGGER.info("Processing line: " + line);

            // Look for lines containing "Data:" (case-insensitive)
            if (line.contains("Data:")) {
                // Split by "Data:" and get the part after it
                String[] parts = line.split("Data:");
                if (parts.length > 1) {
                    // Get the data value and trim whitespace
                    String dataValue = parts[1].trim();

                    // Remove any trailing pipe character or other delimiters if present
                    dataValue = dataValue.split("\\|")[0].trim();

                    LOGGER.info("Extracted data value: " + dataValue);
                    return dataValue;
                }
            }
        }

        LOGGER.warning("No 'Data:' pattern found in output, returning empty string");
        return "";
    }

    /**
     * Resolve variable references in communication operations
     */
    private static JSONArray resolveCommunicationOperationVariables(JSONArray operations) {
        JSONArray resolvedArray = new JSONArray();

        for (int i = 0; i < operations.length(); i++) {
            JSONObject op = operations.getJSONObject(i);
            JSONObject resolvedOp = new JSONObject();

            // Resolve each field in the operation
            for (String key : op.keySet()) {
                Object value = op.get(key);
                if (value instanceof String) {
                    String strValue = (String) value;
                    // Resolve home directory references
                    strValue = resolveHome(strValue);
                    // Resolve variable references
                    strValue = resolveVariableReferences(strValue);
                    // Preserve original type after resolution
                    resolvedOp.put(key, convertToOriginalType(strValue));
                } else {
                    resolvedOp.put(key, value);
                }
            }

            resolvedArray.put(resolvedOp);
        }

        return resolvedArray;
    }

    /**
     * Convert a string value back to its original JSON type (boolean, integer, double)
     * This is needed because all parameters come through Map<String, String> which loses type info.
     *
     * @param value the string representation
     * @return the value as its original type (Boolean, Long, Double) or the original String
     */
    private static Object convertToOriginalType(String value) {
        if (value == null) {
            return JSONObject.NULL;
        }
        // Boolean check
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        // Integer/Long check
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {}
        // Double/Float check
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {}
        // Return as string
        return value;
    }

    /**
     * Capture output from the process input stream
     */
    private static String captureOutput(InputStream inputStream) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                LOGGER.info(line);
            }
        }
        return output.toString();
    }
}
