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
import static com.me.util.GOATCommonConstants.Registry_Util_EXE;

/**
 * Handler for registry operations using Registry_Util_EXE
 * Supports read, write, delete operations with note storage using only extracted data value in output
 * Parameters (action, root, path, key_name, value, value_type, wow_mode) are read directly from the operation's parameters.
 */
public class RegistryOperation {
    private static final Logger LOGGER = LogManager.getLogger(
            RegistryOperation.class, LogManager.LOG_TYPE.FW);

    public static boolean executeOperation(Operation op) {
        if (op == null) {
            LOGGER.warning("Operation is null");
            return false;
        }

        StringBuilder remarkBuilder = new StringBuilder();
        Path tempJsonFile = null;

        try {
            // Build a JSON object from the operation's direct parameters
            JSONObject registryOp = new JSONObject();
            Map<String, String> params = op.getParameters();

            if (params == null || params.isEmpty()) {
                LOGGER.warning("No parameters found for registry operation");
                return false;
            }

            // Copy all parameters except internal ones (note, remarks, etc.) into the registry operation JSON
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String key = entry.getKey();
                // Skip non-registry internal parameters
                if ("note".equals(key) || "remarks".equals(key)) {
                    continue;
                }
                registryOp.put(key, entry.getValue());
            }

            // Wrap in a JSON array
            JSONArray registryOperationsArray = new JSONArray();
            registryOperationsArray.put(registryOp);

            // Resolve variables in registry operations
            registryOperationsArray = resolveRegistryOperationVariables(registryOperationsArray);

            // Write JSON array to a temp file
            tempJsonFile = Files.createTempFile("registry_ops_", ".json");
            try (BufferedWriter writer = Files.newBufferedWriter(tempJsonFile)) {
                writer.write(registryOperationsArray.toString(2)); // pretty print
            }

            // Run Registry EXE with temp JSON file path
            ProcessBuilder builder = new ProcessBuilder(
                    Registry_Util_EXE,
                    tempJsonFile.toAbsolutePath().toString()
            );
            
            File registryLogDir = new File(new File(Registry_Util_EXE).getParentFile(), "Logs" + File.separator + "Registry");
            if (!registryLogDir.exists()) {
                registryLogDir.mkdirs();
            }
            builder.directory(registryLogDir);
            
            builder.redirectErrorStream(true);

            LOGGER.info("Starting Registry operation process...");
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
            LOGGER.log(Level.SEVERE, "Registry operation failed", e);
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
     * Extract the data value from Registry_Util_EXE output
     *
     * Expected output format:
     * Operation_1|PASSED|String value read successfully|Data:false
     * SUMMARY|Total:1|Passed:1|Failed:0
     *
     * Extracts: "false"
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
     * Resolve variable references in registry operations
     */
    private static JSONArray resolveRegistryOperationVariables(JSONArray operations) {
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
                    resolvedOp.put(key, strValue);
                } else {
                    resolvedOp.put(key, value);
                }
            }

            resolvedArray.put(resolvedOp);
        }

        return resolvedArray;
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


