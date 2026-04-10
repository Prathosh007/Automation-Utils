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
import static com.me.util.GOATCommonConstants.Agent_GUI_UTILS_EXE;

/**
 * Handler for native GUI operations using Agent_GUI_UTILS_EXE
 * Supports GUI automation operations with note storage using only extracted data value in output
 * Parameters are read directly from the operation's parameters.
 */
public class NativeGUIOperationHandler {
    private static final Logger LOGGER = LogManager.getLogger(
            NativeGUIOperationHandler.class, LogManager.LOG_TYPE.FW);

    public static boolean executeOperation(Operation op) {
        if (op == null) {
            LOGGER.warning("Operation is null");
            return false;
        }

        StringBuilder remarkBuilder = new StringBuilder();
        Path tempJsonFile = null;

        try {
            Map<String, String> params = op.getParameters();

            if (params == null || params.isEmpty()) {
                LOGGER.warning("No parameters found for native GUI operation");
                return false;
            }

            JSONArray guiOperationsArray;

            // Extract target_app_exe if present (used as CLI argument, not in JSON)
            String targetAppExe = params.containsKey("target_app_exe") ? params.get("target_app_exe") : "";
            if (targetAppExe != null && !targetAppExe.isEmpty()) {
                targetAppExe = resolveHome(targetAppExe);
                targetAppExe = resolveVariableReferences(targetAppExe);
                LOGGER.info("Target app exe: " + targetAppExe);
            } else {
                targetAppExe = "";
            }

            // Check if old format: parameters contain "gui_operations" key with a JSON array
            if (params.containsKey("gui_operations")) {
                LOGGER.info("Detected OLD format: gui_operations array inside parameters");
                String guiOpsJson = params.get("gui_operations");
                LOGGER.info("Raw gui_operations value: " + guiOpsJson);
                guiOperationsArray = new JSONArray(guiOpsJson);
                LOGGER.info("Parsed gui_operations into JSONArray with " + guiOperationsArray.length() + " operations");
            } else {
                // New format: flat parameters, each operation is its own native_gui_operation
                LOGGER.info("Detected NEW format: flat parameters per operation");
                JSONObject guiOp = new JSONObject();

                // Copy all parameters except internal ones (note, remarks, target_app_exe, etc.) into the GUI operation JSON
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    String key = entry.getKey();
                    // Skip internal parameters and target_app_exe (passed as CLI arg)
                    if ("note".equals(key) || "remarks".equals(key) || "target_app_exe".equals(key)) {
                        continue;
                    }
                    // Preserve original JSON types (boolean, number) instead of storing everything as strings
                    guiOp.put(key, convertToOriginalType(entry.getValue()));
                }

                // Wrap in a JSON array
                guiOperationsArray = new JSONArray();
                guiOperationsArray.put(guiOp);
                LOGGER.info("Built single operation JSONArray from flat parameters");
            }

            // Resolve variables in GUI operations
            guiOperationsArray = resolveGuiOperationVariables(guiOperationsArray);

            // Write JSON array to a temp file
            tempJsonFile = Files.createTempFile("gui_ops_", ".json");
            String jsonContent = guiOperationsArray.toString(2); // pretty print
            try (BufferedWriter writer = Files.newBufferedWriter(tempJsonFile)) {
                writer.write(jsonContent);
            }

            LOGGER.info("JSON content being sent to GUI EXE: " + jsonContent);
            LOGGER.info("Temp JSON file path: " + tempJsonFile.toAbsolutePath().toString());

            // Run GUI EXE with temp JSON file path (and optional target_app_exe as CLI argument)
            ProcessBuilder builder;
            if (targetAppExe.isEmpty()) {
                builder = new ProcessBuilder(
                        Agent_GUI_UTILS_EXE,
                        tempJsonFile.toAbsolutePath().toString()
                );
                LOGGER.info("Running GUI EXE without target app");
            } else {
                builder = new ProcessBuilder(
                        Agent_GUI_UTILS_EXE,
                        targetAppExe,
                        tempJsonFile.toAbsolutePath().toString()
                );
                LOGGER.info("Running GUI EXE with target app: " + targetAppExe);
            }

            File guiLogDir = new File(new File(Agent_GUI_UTILS_EXE).getParentFile(), "Logs" + File.separator + "NativeGUI");
            if (!guiLogDir.exists()) {
                guiLogDir.mkdirs();
            }
            builder.directory(guiLogDir);

            builder.redirectErrorStream(true);

            LOGGER.info("Starting Native GUI automation process...");
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
            LOGGER.log(Level.SEVERE, "Native GUI automation failed", e);
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
     * Convert string values back to their original JSON types.
     * Booleans ("true"/"false") become actual booleans,
     * numeric strings become numbers, everything else stays as string.
     */
    private static Object convertToOriginalType(String value) {
        if (value == null) {
            return null;
        }
        // Check for boolean
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        // Check for integer
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            // Not an integer
        }
        // Check for long
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            // Not a long
        }
        // Check for double
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            // Not a double
        }
        // Return as string
        return value;
    }

    /**
     * Extract the data value from Agent_GUI_UTILS_EXE output
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
     * Resolve variable references in GUI operations
     */
    private static JSONArray resolveGuiOperationVariables(JSONArray operations) {
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
