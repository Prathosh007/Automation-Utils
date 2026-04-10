package com.me.testcases;

import java.io.*;
import java.io.FileReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.me.Operation;
import com.me.util.DownloadFile;
import com.me.util.LogManager;
import com.me.util.Uninstall;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static com.me.testcases.ServerUtils.getProductServerHome;
import static com.me.testcases.ServerUtils.getToolServerHome;
import static com.me.util.CommonUtill.deleteOldestUnusedServerFolder;
import static com.me.util.CommonUtill.resolveHome;
import static com.me.util.GOATCommonConstants.*;
import static com.me.util.ServerUtils.getName;
import static com.me.util.ServerUtils.killProcess;
import static com.me.util.Uninstall.uninstall;

/**
 * Handler for exe_install operations using AutoIT scripts
 */
public class ExeInstall {
    private static final Logger LOGGER = LogManager.getLogger(ExeInstall.class, LogManager.LOG_TYPE.FW);

    // Default locations - updated based on new requirements
    public static final String DEFAULT_DOWNLOAD_DIR = "../../downloads";
    public static final String DEFAULT_SCRIPTS_DIR = "../AutoIT/scripts";
    public static final String DEFAULT_AUTOIT_DIR = "../AutoIT";
    public static final String DEFAULT_LOGS_DIR = "../log/autoit";
    public static final String SERVER_DIR_NAME = "ServerDirectory";

    // AutoIT executable name

    // Product configuration - maps product types to their configuration
    private static Map<String, ProductConfig> PRODUCT_CONFIGS = initProductConfigs();

    /**
     * Registry path for installed programs
     */
    private static final String UNINSTALL_REG_PATH =
            "HKEY_LOCAL_MACHINE\\SOFTWARE\\Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall";

    /**
     * Map of product types to their expected service names and display names in registry
     */
    private static final Map<String, ProductInstallInfo> PRODUCT_INSTALL_INFO = initProductInstallInfo();

    /**
     * Represents configuration for a product installation
     */
    private static class ProductConfig {
        String scriptFile;       // AutoIT script filename
        //        String installerPattern; // Pattern for installer filename
        String setupName;        // Setup name used by installer

        ProductConfig(String scriptFile, String setupName) {
            this.scriptFile = scriptFile;
//            this.installerPattern = installerPattern;
            this.setupName = setupName;
        }
    }

    /**
     * Class to hold product installation information
     */
    public static class ProductInstallInfo {
        String serviceName;        // Windows service name
        String displayName;        // Display name in registry
        String uninstallScript;    // Uninstall script relative to product bin directory
        String[] dependentProducts; // Array of dependent product names

        ProductInstallInfo(String serviceName, String displayName, String uninstallScript) {
            this.serviceName = serviceName;
            this.displayName = displayName;
            this.uninstallScript = uninstallScript;
            this.dependentProducts = new String[0]; // Default to empty array
        }

        ProductInstallInfo(String serviceName, String displayName, String uninstallScript, String[] dependentProducts) {
            this.serviceName = serviceName;
            this.displayName = displayName;
            this.uninstallScript = uninstallScript;
            this.dependentProducts = dependentProducts != null ? dependentProducts : new String[0];
        }
    }

    /**
     * Initialize product configuration map
     */
    private static Map<String, ProductConfig> initProductConfigs() {
        Map<String, ProductConfig> configs = new HashMap<>();
        String configFilePath = getToolServerHome() + PRODUCT_SETUP_CONF;

        try {
            // Check if file exists
            File jsonFile = new File(configFilePath);
            if (!jsonFile.exists()) {
                LOGGER.warning("Product setup configuration file not found: " + configFilePath);
                return configs;
            }

            // Read the JSON configuration file
            String jsonContent = new String(Files.readAllBytes(Paths.get(configFilePath)), StandardCharsets.UTF_8);
            LOGGER.info("JSON content loaded, size: " + jsonContent.length() + " bytes");

            // Parse the JSON
            JSONObject jsonObject = new JSONObject(jsonContent);
            LOGGER.info("JSON parsed successfully with " + jsonObject.keySet().size() + " products");

            // Convert all keys to lowercase recursively
            JSONObject updatedJson = convertKeysToLowercase(jsonObject);
            boolean keysChanged = !updatedJson.toString().equals(jsonObject.toString());

            // Process each product with lowercase keys for configs map
            for (String productKey : updatedJson.keySet()) {
                JSONObject productData = updatedJson.getJSONObject(productKey);

                // Extract configuration with lowercase keys
                String scriptFile = productData.optString("scriptfile");
                String setupName = productData.optString("setupname");
                configs.put(productKey, new ProductConfig(scriptFile, setupName));

                LOGGER.info("Added config for product: " + productKey +
                        ", scriptFile: " + scriptFile +
                        ", setupName: " + setupName);
            }

            // Only write back if keys were actually changed
            if (keysChanged) {
                try (FileWriter writer = new FileWriter(configFilePath)) {
                    writer.write(updatedJson.toString(2)); // Use indent of 2 for pretty formatting
                    LOGGER.info("Updated product configuration file with lowercase keys: " + configFilePath);
                }
            } else {
                LOGGER.info("No keys needed conversion to lowercase");
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading/writing product configuration file: " + configFilePath, e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error loading product configuration", e);
        }

        return configs;
    }

    /**
     * Recursively converts all keys in a JSONObject to lowercase
     */
    private static JSONObject convertKeysToLowercase(JSONObject source) {
        JSONObject result = new JSONObject();

        // Process each key in the source object
        for (String key : source.keySet()) {
            String lowercaseKey = key.toLowerCase();
            Object value = source.get(key);

            // If the value is a JSONObject, recursively convert its keys
            if (value instanceof JSONObject) {
                result.put(lowercaseKey, convertKeysToLowercase((JSONObject) value));
            }
            // If the value is a JSONArray, check if any items are JSONObjects
            else if (value instanceof JSONArray) {
                JSONArray array = (JSONArray) value;
                JSONArray newArray = new JSONArray();

                for (int i = 0; i < array.length(); i++) {
                    Object item = array.get(i);
                    if (item instanceof JSONObject) {
                        newArray.put(convertKeysToLowercase((JSONObject) item));
                    } else {
                        newArray.put(item);
                    }
                }

                result.put(lowercaseKey, newArray);
            }
            // For simple values, just add with lowercase key
            else {
                result.put(lowercaseKey, value);
            }

            // Log key changes
            if (!key.equals(lowercaseKey)) {
                LOGGER.info("Converting key: " + key + " → " + lowercaseKey);
            }
        }

        return result;
    }


    /**
     * Simple JSON parser for the specific structure of product-setup.json
     */
    private static Map<String, Map<String, String>> parseJsonConfig(String jsonContent) {
        Map<String, Map<String, String>> result = new HashMap<>();

        // Remove braces at beginning and end
        jsonContent = jsonContent.trim();
        if (jsonContent.startsWith("{")) {
            jsonContent = jsonContent.substring(1);
        }
        if (jsonContent.endsWith("}")) {
            jsonContent = jsonContent.substring(0, jsonContent.length() - 1);
        }

        // Split into product entries
        int depth = 0;
        StringBuilder currentSection = new StringBuilder();
        String currentProductType = null;

        for (int i = 0; i < jsonContent.length(); i++) {
            char c = jsonContent.charAt(i);

            if (c == '{') {
                depth++;
                currentSection.append(c);
            } else if (c == '}') {
                depth--;
                currentSection.append(c);

                if (depth == 0 && currentProductType != null) {
                    // Process completed section
                    Map<String, String> productConfig = parseProductSection(currentSection.toString());
                    result.put(currentProductType, productConfig);
                    currentSection.setLength(0);
                    currentProductType = null;
                }
            } else if (depth == 0 && c == '"') {
                // Extract product type
                StringBuilder typeBuilder = new StringBuilder();
                i++;
                while (jsonContent.charAt(i) != '"') {
                    typeBuilder.append(jsonContent.charAt(i));
                    i++;
                }
                currentProductType = typeBuilder.toString();

                // Skip until opening brace
                while (i < jsonContent.length() && jsonContent.charAt(i) != '{') {
                    i++;
                }
                i--; // Adjust for the loop increment
            } else if (depth > 0) {
                currentSection.append(c);
            }
        }

        return result;
    }

    /**
     * Parse individual product section
     */
    private static Map<String, String> parseProductSection(String section) {
        Map<String, String> config = new HashMap<>();

        section = section.trim();
        if (section.startsWith("{")) {
            section = section.substring(1);
        }
        if (section.endsWith("}")) {
            section = section.substring(0, section.length() - 1);
        }

        // Extract key-value pairs
        Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(section);

        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            config.put(key, value);
        }

        return config;
    }

    /**
     * Initialize product installation info map from configuration file
     */
    private static Map<String, ProductInstallInfo> initProductInstallInfo() {
        Map<String, ProductInstallInfo> info = new HashMap<>();
        String configFilePath = getToolServerHome() + PRODUCT_SETUP_CONF;

        try {
            // Verify file exists
            File configFile = new File(configFilePath);
            if (!configFile.exists() || !configFile.isFile()) {
                LOGGER.severe("Product setup configuration file not found: " + configFilePath);
                // Add default fallback values for critical products
//                addDefaultProductInfo(info);
                return info;
            }

            // Read the JSON configuration file
            String jsonContent = new String(Files.readAllBytes(Paths.get(configFilePath)), StandardCharsets.UTF_8);

            if (jsonContent.trim().isEmpty()) {
                LOGGER.severe("Empty product configuration file: " + configFilePath);
                addDefaultProductInfo(info);
                return info;
            }

            // Use org.json library to parse the JSON content
            JSONObject jsonObject = new JSONObject(jsonContent);

            // Process each product entry
            for (String productType : jsonObject.keySet()) {
                JSONObject productConfig = jsonObject.getJSONObject(productType);

                String serviceName = productConfig.optString("servicename");
                String displayName = productConfig.optString("displayname");
                String uninstallScript = productConfig.optString("uninstallscript");

                // Read dependent products field
                String[] dependentProducts = new String[0];
                if (productConfig.has("depended_product")) {
                    JSONArray depArray = productConfig.getJSONArray("depended_product");
                    dependentProducts = new String[depArray.length()];
                    for (int i = 0; i < depArray.length(); i++) {
                        dependentProducts[i] = depArray.getString(i).trim().toLowerCase();
                    }
                    LOGGER.info("Found dependent products for " + productType + ": " + String.join(", ", dependentProducts));
                }

                // Validate required fields
                if (serviceName.isEmpty() || displayName.isEmpty()) {
                    LOGGER.warning("Missing required fields for product: " + productType);
                    continue;
                }

                // Store with lowercase key for consistent lookups
                info.put(productType.toLowerCase(), new ProductInstallInfo(serviceName, displayName, uninstallScript, dependentProducts));
                LOGGER.info("Loaded install info for product: " + productType +
                        ", serviceName: " + serviceName +
                        ", displayName: " + displayName +
                        ", dependentProducts: " + (dependentProducts.length > 0 ? String.join(", ", dependentProducts) : "none"));
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading product configuration file: " + configFilePath, e);
            addDefaultProductInfo(info);
        } catch (JSONException e) {
            LOGGER.log(Level.SEVERE, "Error parsing JSON from configuration file: " + configFilePath, e);
            addDefaultProductInfo(info);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error loading product configuration", e);
            addDefaultProductInfo(info);
        }

        return info;
    }

    /**
     * Add default product information as fallback
     */
    private static void addDefaultProductInfo(Map<String, ProductInstallInfo> info) {
        LOGGER.info("Adding default product installation information");
        info.put("endpointcentral", new ProductInstallInfo("uems_service",
                "ManageEngine UEMS - Server",
                "bin\\scripts\\uninstall.bat"));
        info.put("patch_manager", new ProductInstallInfo("PatchManager_Server",
                "ManageEngine Patch Manager Plus",
                "bin\\scripts\\uninstall.bat"));
        // Add other critical products as needed
    }

    /**
     * Execute an exe_install operation
     *
     * @param op The operation to execute
     * @return true if successful, false if failed
     */
    public static boolean executeOperation(Operation op) {
        if (op == null) {
            LOGGER.severe("Operation is null");
            return false;
        }

        String productType = op.getParameter("product_name").toLowerCase();
        if (productType == null || productType.isEmpty()) {
            op.setRemarks("Error: product_name parameter is required");
            return false;
        }

        LOGGER.info("Executing exe_install for product type: " + productType);

        ProductConfig config = PRODUCT_CONFIGS.get(productType.toLowerCase());
        if (config == null) {
            op.setRemarks("Error: Unknown product type: " + productType +
                    ". Supported products: " + String.join(", ", PRODUCT_CONFIGS.keySet()));
            return false;
        }

        StringBuilder preconditionRemarks = new StringBuilder();
        boolean preconditionsOk = handlePreconditions(productType, preconditionRemarks, op);
        if (!preconditionsOk) {
            op.setRemarks("Error during precondition check: " + op.getRemarks() + "  \n " + preconditionRemarks);
            return false;
        } else if (preconditionRemarks.length() > 0) {
            LOGGER.info("Precondition results: " + preconditionRemarks.toString());
        }

        String installerName = op.getParameter("installer_name");
        String installPath = op.getParameter("install_path");
        String setupName = op.getParameter("setup_name");
        boolean deleteOldestUnusedServerFolder = true;
        String cleanupParam = op.getParameter("default_installation_folder_cleanup");
        if (cleanupParam != null && !cleanupParam.isEmpty()) {
            deleteOldestUnusedServerFolder = Boolean.parseBoolean(cleanupParam);
        }

        if (deleteOldestUnusedServerFolder) {
            deleteOldestUnusedServerFolder(getToolServerHome() + DEFAULT_SERVER_DIR);
        }

        if (installerName == null || installerName.isEmpty()) {
            LOGGER.info("Extracting filename from URL");
            String url = op.getParameter("url");
            try {
                URL urlObj = new URL(url);
                String urlPath = urlObj.getPath();
                installerName = urlPath.substring(urlPath.lastIndexOf('/') + 1);

                // Try to extract filename from query parameters if present
                String query = urlObj.getQuery();
                if (query != null) {
                    for (String param : query.split("&")) {
                        String[] pair = param.split("=", 2);
                        if (pair.length == 2 && !pair[1].isEmpty()) {
                            installerName = pair[1];
                            LOGGER.info("Extracted filename from query parameter key '" + pair[0] + "': " + installerName);
                            break;
                        }
                    }
                }

            } catch (Exception e) {
                op.setRemarks("Failed to extract filename from URL kindly check the URL");
                LOGGER.info("Failed to extract filename from URL ; " + e);
                return false;
            }
        }

        String baseDir = op.getParameter("base_dir");
        String downloadDir = op.getParameter("download_dir");
        String scriptsDir = op.getParameter("scripts_dir");
        String autoItDir = op.getParameter("autoit_dir");
        String logsDir = op.getParameter("logs_dir");

        if (baseDir == null || baseDir.isEmpty()) {
            baseDir = System.getProperty("goat.home", System.getProperty("user.dir"));
        }
        if (downloadDir == null || downloadDir.isEmpty()) {
            downloadDir = Paths.get(baseDir, DEFAULT_DOWNLOAD_DIR).toString();
        }
        if (scriptsDir == null || scriptsDir.isEmpty()) {
            scriptsDir = Paths.get(baseDir, DEFAULT_SCRIPTS_DIR).toString();
        }
        if (autoItDir == null || autoItDir.isEmpty()) {
            autoItDir = Paths.get(baseDir, DEFAULT_AUTOIT_DIR).toString();
        }
        if (logsDir == null || logsDir.isEmpty()) {
            logsDir = Paths.get(baseDir, DEFAULT_LOGS_DIR).toString();
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
//        boolean isServerProduct = productType.contains("server") ||
//                productType.equals("desktopcentral") ||
//                productType.equals("endpointcentral");
        if (installPath == null || installPath.isEmpty()) {
            Path serverDir = Paths.get(new File(baseDir).getParent()).resolve(SERVER_DIR_NAME);
            installPath = serverDir.resolve(timestamp).toString();
        }
        if (setupName == null || setupName.isEmpty()) {
            setupName = config.setupName;
        }

        new File(logsDir).mkdirs();
        Path serverDir = Paths.get(new File(baseDir).getParent()).resolve(SERVER_DIR_NAME);
        if (!Files.exists(serverDir)) {
            try {
                Files.createDirectories(serverDir);
                LOGGER.info("Created server directory: " + serverDir);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to create server directory: " + serverDir, e);
            }
        }

        String scriptFile = config.scriptFile;
        String scriptPath = Paths.get(scriptsDir, scriptFile).toString();
        String installerPath = Paths.get(downloadDir, installerName).toString();
        String logFilePath = Paths.get(logsDir, "autoit_" + productType + ".log").toString();

        File scriptFileObj = new File(scriptPath);
        if (!scriptFileObj.exists()) {
            op.setRemarks("Error: Script file not found: " + scriptPath +
                    ". Make sure you have the AutoIT scripts in the correct location.");
            return false;
        }
        // Kill the process if already running
        killProcess(getName(op.getParameter("url")));
        boolean isFileDownloaded = DownloadFile.downloadFile(op.getParameter("url"), downloadDir,"sambathkumar.mm:4f96f228973e4c3ab98fa671fcb88712" , op);
        if (!isFileDownloaded){
            LOGGER.severe("Installer download failed, cannot proceed with installation.");
            op.setRemarks("Error: Installer download failed, cannot proceed with installation.");
            return false;
        }

        File installerFile = new File(installerPath);

        LOGGER.info("Installer path: " + installerFile);
        LOGGER.info("Install path: " + installPath);
        LOGGER.info("Script path: " + scriptPath);
        LOGGER.info("Log file path: " + logFilePath);
        LOGGER.info("Base directory: " + baseDir);
        LOGGER.info("Download directory: " + downloadDir);
        LOGGER.info("AutoIT directory: " + autoItDir);

        LOGGER.info("Installer file path: " + installerFile.getAbsolutePath());
        LOGGER.info("Checking installer file at: " + installerPath);
        if (!installerFile.exists()) {
            op.setRemarks("Error: Installer file not found: " + installerPath +
                    ". Please download the installer first.");
            LOGGER.severe("Installer not found: " + installerPath);
            File downloadDirFile = new File(downloadDir);
            if (downloadDirFile.exists() && downloadDirFile.isDirectory()) {
                LOGGER.info("Searching for installer matching pattern in: " + downloadDir);
                String basePattern = installerName.replace(".exe", "");
                File[] possibleInstallers = downloadDirFile.listFiles((dir, name) ->
                        name.toLowerCase().contains(basePattern.toLowerCase()));
                if (possibleInstallers != null && possibleInstallers.length > 0) {
                    installerFile = possibleInstallers[0];
                    installerPath = installerFile.getAbsolutePath();
                    LOGGER.info("Found alternative installer: " + installerPath);
                    op.setRemarks("Warning: Specified installer not found. Using: " + installerFile.getName());
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }

        if (!new File(installPath).exists()) {
            LOGGER.info("Installation path not found, creating directory: " + installPath);
            new File(installPath).mkdirs();
            LOGGER.info("Installation folder created successfully in  " + installPath);
        }

        String autoItPath = Paths.get(autoItDir, AUTOIT_EXE).toString();
        String[] command = null;
        try {
            command = new String[]{
                    autoItPath,
                    new File(scriptPath).getCanonicalPath(),
                    new File(installerPath).getCanonicalPath(),
                    new File(installPath).getCanonicalPath(),
                    new File(logFilePath).getCanonicalPath(),
                    setupName
            };
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        LOGGER.info("Executing AutoIT script directly: " + String.join(" ", command));
        StringBuilder remarks = new StringBuilder();
//        remarks.append("Executing AutoIT installation directly:\n");
//        remarks.append("Product Type: ").append(productType).append("\n");
//        remarks.append("Installer: ").append(installerPath).append("\n");
//        remarks.append("Install Path: ").append(installPath).append("\n");
//        remarks.append("Setup Name: ").append(setupName).append("\n");
//        remarks.append("Command: ").append(String.join(" ", command)).append("\n");

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(autoItDir));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            int exitCode = process.waitFor();
            LOGGER.info("AutoIT process exited with code: " + exitCode);
            remarks.append("AutoIT process exited with code: ").append(exitCode).append("\n");
            if (exitCode == 0) {
//                if (waitForServerHome(10, 5)) { // Wait for up to 10 minutes, checking every 5 seconds
//                    remarks.append("Server home after installation: ").append(getProductServerHome());
//                    remarks.append("\nInstallation completed successfully.\n");
//                    LOGGER.info("Installation completed successfully for product: " + productType);
//                    op.setRemarks(remarks.toString());
//                    return true;
//                } else {
//                    remarks.append("Autoit exit with exit code of 0 but server home path was not found\n");
//                    LOGGER.severe("Autoit exit with exit code of 0 but server home path was not found");
//                    op.setRemarks(remarks.toString());
//                    return false;
//                }
                return true; // Installation successful
            } else {
                remarks.append("Installation failed : \n");
                remarks.append(output.toString()).append("\n");
                if (new File(logFilePath).exists()) {
                    remarks.append("See AutoIT log file for details: " + logFilePath + "\n");
                }
                op.setRemarks(remarks.toString());
                return false;
            }
        } catch (Exception e) {
            LOGGER.severe("Error executing AutoIT script: " + e.getMessage());
            remarks.append("Error executing AutoIT script: ").append(e.getMessage()).append("\n");
            op.setRemarks(remarks.toString());
            return false;
        }
    }

    public static void changeCaseSensitive() {
        String configFilePath = getToolServerHome() + PRODUCT_SETUP_CONF;
        LOGGER.info("Reading and updating JSON configuration file: " + configFilePath);

        try {
            // Read the existing JSON file
            File configFile = new File(configFilePath);
            if (!configFile.exists()) {
                LOGGER.severe("Configuration file not found: " + configFilePath);
                return;
            }

            String jsonContent = new String(Files.readAllBytes(Paths.get(configFilePath)), StandardCharsets.UTF_8);
            JSONObject originalJson = new JSONObject(jsonContent);

            // Create a new JSON object with lowercase keys but preserving structure
            JSONObject updatedJson = new JSONObject();

            for (String key : originalJson.keySet()) {
                String lowerKey = key.toLowerCase();
                JSONObject productData = originalJson.getJSONObject(key);

                // Keep the same structure but with lowercase product key
                updatedJson.put(lowerKey, productData);
                LOGGER.info("Processed product key: " + key + " -> " + lowerKey);
            }

            // Write the updated JSON back to file with proper formatting
            try (FileWriter writer = new FileWriter(configFilePath)) {
                writer.write(updatedJson.toString(2)); // Use indent of 2 for pretty formatting
                LOGGER.info("JSON file updated successfully with lowercase product keys");
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading/writing JSON configuration file", e);
        } catch (JSONException e) {
            LOGGER.log(Level.SEVERE, "Error parsing JSON configuration", e);
        }
    }

    /**
     * Gets the service name for a given product type from the product-setup.json configuration file.
     *
     * @param productType The product type to look up (e.g., "agent", "MDMServer")
     * @return The service name for the product type, or null if not found
     */
    public static String getServiceNameForProduct(String productType) {
        try {
            // Path to the product-setup.json file
            String jsonFilePath = "product_package/conf/product-setup.json";

            // Read file content
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(jsonFilePath))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
            }

            // Extract JSON object for the product type
            int startIndex = content.indexOf("\"" + productType + "\"");
            if (startIndex == -1) {
                return null; // Product type not found
            }

            // Find serviceName property
            int serviceNameIndex = content.indexOf("\"serviceName\"", startIndex);
            if (serviceNameIndex == -1) {
                return null; // serviceName property not found
            }

            // Extract the service name value
            int valueStartIndex = content.indexOf("\"", serviceNameIndex + 13) + 1;
            int valueEndIndex = content.indexOf("\"", valueStartIndex);

            return content.substring(valueStartIndex, valueEndIndex);

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean waitForServerHome(int maxWaitMinutes, int checkIntervalSeconds) {
        long maxWaitMillis = maxWaitMinutes * 60L * 1000L;
        long checkIntervalMillis = checkIntervalSeconds * 1000L;
        long waited = 0;
        String serverHome = getProductServerHome();
        while (waited < maxWaitMillis) {
            try {
                if (new File(serverHome).exists()) {
                    LOGGER.info("Server home is set: " + serverHome);
                    return true; // Server home is set, exit the loop
                }
                Thread.sleep(checkIntervalMillis);
                LOGGER.info("Waiting for server home to be set, current waited time: " + (waited / 1000) + " seconds");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            waited += checkIntervalMillis;
            serverHome = getProductServerHome();
        }
        return false; // Timeout reached, server home not set
    }


    /**
     * Handle preconditions - check for previous installations and clean up if needed
     *
     * @param productType The product type to check
     * @param remarks     StringBuilder to add remarks about the precondition check
     * @return true if preconditions are met (either no previous install or successfully cleaned up)
     */
    static boolean handlePreconditions(String productType, StringBuilder remarks, Operation operation) {
        LOGGER.info("========================================");
        LOGGER.info("PRECONDITION CHECK STARTED");
        LOGGER.info("Product Type: " + productType);
        LOGGER.info("========================================");

        ProductInstallInfo installInfo = PRODUCT_INSTALL_INFO.get(productType.toLowerCase());
        LOGGER.info("Available product types in configuration: " + PRODUCT_INSTALL_INFO.keySet());

        if (installInfo == null) {
            LOGGER.warning("PRECONDITION CHECK - NO CONFIGURATION FOUND");
            LOGGER.warning("No installation info found for product: " + productType);
            LOGGER.warning("Skipping precondition check and proceeding with installation");
            remarks.append("No installation info found for this product, skipping precondition check.\n");
            LOGGER.info("========================================");
            LOGGER.info("PRECONDITION CHECK COMPLETED - SKIPPED");
            LOGGER.info("========================================");
            return true;
        }

        LOGGER.info("Product configuration loaded successfully");
        LOGGER.info("Service Name: " + installInfo.serviceName);
        LOGGER.info("Display Name: " + installInfo.displayName);
        LOGGER.info("Uninstall Script: " + installInfo.uninstallScript);

        // Log dependent products if any
        if (installInfo.dependentProducts != null && installInfo.dependentProducts.length > 0) {
            LOGGER.info("Dependent Products: " + String.join(", ", installInfo.dependentProducts));
            remarks.append("ℹ This product has dependent products: ").append(String.join(", ", installInfo.dependentProducts)).append("\n");
        } else {
            LOGGER.info("No dependent products configured");
        }

        boolean previousInstallFound = false;
        String serviceHome = null;
        String registryEntryPath = null;

        // Step 1: Check for running service of current product
        LOGGER.info("----------------------------------------");
        LOGGER.info("STEP 1: CHECKING FOR EXISTING SERVICE");
        LOGGER.info("----------------------------------------");

        if (installInfo.serviceName != null && !installInfo.serviceName.isEmpty()) {
            LOGGER.info("Searching for service: " + installInfo.serviceName);

            serviceHome = getServerHomeFromService(installInfo.serviceName);

            if (serviceHome != null) {
                previousInstallFound = true;
                LOGGER.info("SUCCESS - Service found");
                LOGGER.info("Service Name: " + installInfo.serviceName);
                LOGGER.info("Service Home Location: " + serviceHome);
                remarks.append("✓ Found existing service: ").append(installInfo.serviceName)
                        .append(" at location: ").append(serviceHome).append("\n");
            } else {
                LOGGER.info("RESULT - Service not found or not running");
                LOGGER.info("Service Name: " + installInfo.serviceName);
            }
        } else {
            LOGGER.info("SKIPPED - No service name configured for this product");
        }

        // Step 2: Check registry for installation of current product
        LOGGER.info("----------------------------------------");
        LOGGER.info("STEP 2: CHECKING REGISTRY FOR INSTALLATION");
        LOGGER.info("----------------------------------------");

        if (installInfo.displayName != null && !installInfo.displayName.isEmpty()) {
            LOGGER.info("Searching registry for display name: " + installInfo.displayName);

            registryEntryPath = findProductInRegistry(operation.getParameter("product_name"));

            if (registryEntryPath != null) {
                previousInstallFound = true;
                LOGGER.info("SUCCESS - Registry entry found");
                LOGGER.info("Registry Path: " + registryEntryPath);
                remarks.append("✓ Found registry entry: ").append(installInfo.displayName).append("\n");

                String registryInstallPath = getInstallLocationFromRegistry(registryEntryPath);

                if (registryInstallPath != null && !registryInstallPath.isEmpty()) {
                    LOGGER.info("SUCCESS - Installation location found in registry");
                    LOGGER.info("Registry Install Location: " + registryInstallPath);
                    remarks.append("  Registry install location: ").append(registryInstallPath).append("\n");

                    if (serviceHome == null) {
                        LOGGER.info("Using registry installation path as service home");
                        serviceHome = registryInstallPath;
                    } else {
                        LOGGER.info("Service home already determined from service, using: " + serviceHome);
                    }
                } else {
                    LOGGER.warning("WARNING - Registry entry found but installation path is empty");
                    remarks.append("⚠ Registry entry found but no valid installation path\n");
                }
            } else {
                LOGGER.info("RESULT - No registry entry found");
                LOGGER.info("Display Name searched: " + installInfo.displayName);
            }
        } else {
            LOGGER.warning("SKIPPED - Display name is null or empty, cannot search registry");
        }

        // Step 3: Check dependent products for services and registry (only for detection, not cleanup)
        if (installInfo.dependentProducts != null && installInfo.dependentProducts.length > 0) {
            LOGGER.info("========================================");
            LOGGER.info("CHECKING DEPENDENT PRODUCTS FOR EXISTING INSTALLATIONS");
            LOGGER.info("========================================");

            for (String depProduct : installInfo.dependentProducts) {
                LOGGER.info("----------------------------------------");
                LOGGER.info("Checking dependent product: " + depProduct);
                LOGGER.info("----------------------------------------");

                ProductInstallInfo depInstallInfo = PRODUCT_INSTALL_INFO.get(depProduct.toLowerCase());

                if (depInstallInfo == null) {
                    LOGGER.warning("No configuration found for dependent product: " + depProduct);
                    remarks.append("⚠ Warning: No configuration found for dependent product: ").append(depProduct).append("\n");
                    continue;
                }

                // Check dependent product service
                if (depInstallInfo.serviceName != null && !depInstallInfo.serviceName.isEmpty()) {
                    LOGGER.info("Checking for dependent product service: " + depInstallInfo.serviceName);
                    String depServiceHome = getServerHomeFromService(depInstallInfo.serviceName);

                    if (depServiceHome != null) {
                        previousInstallFound = true;
                        LOGGER.info("SUCCESS - Dependent product service found: " + depInstallInfo.serviceName);
                        LOGGER.info("Service Home Location: " + depServiceHome);
                        remarks.append("✓ Found existing dependent product service: ").append(depInstallInfo.serviceName)
                                .append(" at location: ").append(depServiceHome).append("\n");
                    } else {
                        LOGGER.info("RESULT - Dependent product service not found: " + depInstallInfo.serviceName);
                    }
                }

                // Check dependent product registry
                if (depInstallInfo.displayName != null && !depInstallInfo.displayName.isEmpty()) {
                    LOGGER.info("Checking registry for dependent product: " + depProduct);
                    String depRegistryPath = findProductInRegistry(depProduct);

                    if (depRegistryPath != null) {
                        previousInstallFound = true;
                        LOGGER.info("SUCCESS - Dependent product registry entry found");
                        LOGGER.info("Registry Path: " + depRegistryPath);
                        remarks.append("✓ Found dependent product registry entry: ").append(depInstallInfo.displayName).append("\n");
                    } else {
                        LOGGER.info("RESULT - No registry entry found for dependent product: " + depProduct);
                    }
                }
            }

            LOGGER.info("========================================");
            LOGGER.info("DEPENDENT PRODUCTS CHECK COMPLETED");
            LOGGER.info("========================================");
        }

        // Step 4: If previous installation found (main or dependent), clean it up
        if (previousInstallFound) {
            LOGGER.info("========================================");
            LOGGER.info("PREVIOUS INSTALLATION DETECTED - CLEANUP REQUIRED");
            LOGGER.info("========================================");
            remarks.append("\n⚠ Previous installation detected, initiating cleanup...\n");
            remarks.append("Note: Uninstall process will handle both main product and its dependencies automatically.\n\n");

            // Try to stop service if it's running
            LOGGER.info("----------------------------------------");
            LOGGER.info("STEP 3: STOPPING SERVICE");
            LOGGER.info("----------------------------------------");

            if (installInfo.serviceName != null && !installInfo.serviceName.isEmpty()) {
                LOGGER.info("Attempting to stop service: " + installInfo.serviceName);
                boolean serviceStopped = stopService(installInfo.serviceName, remarks);

                if (serviceStopped) {
                    LOGGER.info("SUCCESS - Service stopped successfully");
                } else {
                    LOGGER.warning("WARNING - Failed to stop service: " + installInfo.serviceName);
                    LOGGER.warning("Continuing with uninstall process anyway");
                    remarks.append("⚠ Warning: Failed to stop service, continuing with uninstall\n");
                }
            } else {
                LOGGER.info("SKIPPED - No service configured to stop");
            }

            // Try to run uninstall script if we know the server home
            LOGGER.info("----------------------------------------");
            LOGGER.info("STEP 4: RUNNING UNINSTALL SCRIPT");
            LOGGER.info("Note: This will uninstall both main product and dependent products");
            LOGGER.info("----------------------------------------");

            boolean uninstallSuccess = false;
                if (installInfo.uninstallScript != null) {
                    LOGGER.info("Uninstall Autoit script path: " + installInfo.uninstallScript);
                    File uninstallScriptFile = new File(resolveHome(installInfo.uninstallScript));

                    if (uninstallScriptFile.exists() && (uninstallScriptFile.toPath().endsWith(".au3") || uninstallScriptFile.toPath().endsWith(".exe"))) {
                        LOGGER.info("Executing uninstall script from file system");
                        if (serviceHome == null) {
                            LOGGER.warning("Service home is unknown, cannot run uninstall script from file system");
                            remarks.append("⚠ Warning: Service home unknown, cannot run uninstall script\n");
                        } else {
                        uninstallSuccess = runUninstallScript(serviceHome, installInfo.uninstallScript, remarks);
                        }
                    }
                }
                if (!uninstallSuccess) {
                    LOGGER.info("Uninstall script not found in file system, attempting registry-based uninstall");
                    LOGGER.info("Note: Registry-based uninstall will handle dependent products automatically");
                    try {
                        boolean isRegistryCleanupDone = uninstall(operation.getParameter("product_name").toLowerCase(), remarks);
                        LOGGER.info("Is Registry cleanup done : " + isRegistryCleanupDone);
                        uninstallSuccess = Uninstall.uninstallation(operation);
                        LOGGER.info("Is Uninstall bat run successfully : " + uninstallSuccess);
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "FAILED - Error during registry-based uninstall: " + e.getMessage(), e);
                    }
                }

                if (uninstallSuccess) {
                    LOGGER.info("SUCCESS - Uninstall completed successfully (including dependent products)");
                    remarks.append("✓ Uninstall script executed successfully (including dependent products)\n");
                } else {
                    LOGGER.warning("WARNING - Uninstall script execution failed or returned errors");
                    LOGGER.warning("Continuing with registry cleanup");
                    remarks.append("⚠ Warning: Uninstall script failed, attempting manual cleanup\n");
                }

            // If we found registry entry, try to remove it
            LOGGER.info("----------------------------------------");
            LOGGER.info("STEP 5: CLEANING UP REGISTRY ENTRY");
            LOGGER.info("----------------------------------------");


            boolean registryCleanupSuccess = true;
            // Remove registry entries for all dependent products one by one
            if (installInfo.dependentProducts != null && installInfo.dependentProducts.length > 0) {
                for (String depProduct : installInfo.dependentProducts) {
                    String depRegistryPath = findProductInRegistry(depProduct);
                    if (depRegistryPath != null) {
                        previousInstallFound = true;
                        LOGGER.info("SUCCESS - Dependent product registry entry found");
                        LOGGER.info("Registry Path: " + depRegistryPath);
                        remarks.append("✓ Found dependent product registry entry: ").append(depProduct)
                                .append(" at path: ").append(depRegistryPath).append("\n");
                        boolean depRegistryCleanupSuccess = cleanupRegistryEntry(depRegistryPath, remarks);
                        if (depRegistryCleanupSuccess) {
                            LOGGER.info("SUCCESS - Dependent product registry entry cleaned up successfully");
                            remarks.append("✓ Dependent product registry entry removed successfully\n");
                        } else {
                            registryCleanupSuccess = false;
                            LOGGER.info("WARNING - Failed to clean up dependent product registry entry for : " + depProduct);
                            remarks.append("⚠ Warning: Failed to remove dependent product registry entry for :").append(depProduct).append("\n");
                        }
                    }
                }
            } else {
                LOGGER.info("No dependent products to clean up from registry");
            }

            // Final verification that services are gone (main product)
            LOGGER.info("----------------------------------------");
            LOGGER.info("STEP 6: VERIFICATION - SERVICE REMOVAL (MAIN PRODUCT)");
            LOGGER.info("----------------------------------------");

            boolean serviceGone = true;
            if (installInfo.serviceName != null && !installInfo.serviceName.isEmpty()) {
                LOGGER.info("Verifying service removal: " + installInfo.serviceName);
                serviceGone = verifyServiceRemoved(installInfo.serviceName, remarks);

                if (serviceGone) {
                    LOGGER.info("SUCCESS - Service has been removed successfully");
                    remarks.append("✓ Service removal verified\n");
                } else {
                    LOGGER.severe("FAILED - Service still exists after uninstall");
                    LOGGER.severe("Service Name: " + installInfo.serviceName);
                    LOGGER.severe("PRECONDITION CHECK FAILED - Cannot proceed with installation");
                    remarks.append("✗ CRITICAL: Service still exists after cleanup\n");
                    LOGGER.info("========================================");
                    LOGGER.info("PRECONDITION CHECK FAILED");
                    LOGGER.info("========================================");
                    return false;
                }
            } else {
                LOGGER.info("SKIPPED - No service to verify");
            }

            // Verify dependent product services are removed
            if (installInfo.dependentProducts != null && installInfo.dependentProducts.length > 0) {
                LOGGER.info("----------------------------------------");
                LOGGER.info("STEP 6.1: VERIFICATION - DEPENDENT PRODUCT SERVICE REMOVAL");
                LOGGER.info("----------------------------------------");

                for (String depProduct : installInfo.dependentProducts) {
                    ProductInstallInfo depInstallInfo = PRODUCT_INSTALL_INFO.get(depProduct.toLowerCase());

                    if (depInstallInfo != null && depInstallInfo.serviceName != null && !depInstallInfo.serviceName.isEmpty()) {
                        LOGGER.info("Verifying dependent product service removal: " + depInstallInfo.serviceName);
                        boolean depServiceGone = verifyServiceRemoved(depInstallInfo.serviceName, remarks);

                        if (depServiceGone) {
                            LOGGER.info("SUCCESS - Dependent product service removed: " + depInstallInfo.serviceName);
                            remarks.append("✓ Dependent product service removal verified: ").append(depProduct).append("\n");
                        } else {
                            LOGGER.severe("FAILED - Dependent product service still exists: " + depInstallInfo.serviceName);
                            remarks.append("✗ CRITICAL: Dependent product service still exists: ").append(depProduct).append("\n");
                            return false;
                        }
                    }
                }
            }

            // Final verification that registry entries are gone (main product)
            LOGGER.info("----------------------------------------");
            LOGGER.info("STEP 7: VERIFICATION - REGISTRY ENTRY REMOVAL");
            LOGGER.info("----------------------------------------");

            if (registryCleanupSuccess) {
                LOGGER.info("Registry cleanup reported successful");
            }else {
                    LOGGER.warning("WARNING - Registry entry still exists after cleanup");
                    LOGGER.warning("This is not critical, continuing with installation");
                }

            // Verify dependent product registry entries are removed
            if (installInfo.dependentProducts != null && installInfo.dependentProducts.length > 0) {
                LOGGER.info("----------------------------------------");
                LOGGER.info("STEP 7.1: VERIFICATION - DEPENDENT PRODUCT REGISTRY REMOVAL");
                LOGGER.info("----------------------------------------");

                for (String depProduct : installInfo.dependentProducts) {
                    ProductInstallInfo depInstallInfo = PRODUCT_INSTALL_INFO.get(depProduct.toLowerCase());

                    if (depInstallInfo != null) {
                        LOGGER.info("Verifying dependent product registry removal: " + depProduct);
                        String depRegPath = findProductInRegistry(depProduct);

                        if (depRegPath == null) {
                            LOGGER.info("SUCCESS - Dependent product registry entry removed: " + depProduct);
                            remarks.append("✓ Dependent product registry removal verified: ").append(depProduct).append("\n");
                        } else {
                            LOGGER.warning("WARNING - Dependent product registry entry still exists: " + depProduct);
                            remarks.append("⚠ Warning: Dependent product registry entry still exists: ").append(depProduct).append("\n");
                        }
                    }
                }
            }

            LOGGER.info("========================================");
            LOGGER.info("CLEANUP COMPLETED SUCCESSFULLY");
            LOGGER.info("========================================");
            remarks.append("\n✓ Cleanup completed successfully\n");
        } else {
            LOGGER.info("========================================");
            LOGGER.info("NO PREVIOUS INSTALLATION FOUND");
            LOGGER.info("Proceeding with fresh installation");
            LOGGER.info("========================================");
            remarks.append("✓ No previous installation found, ready for fresh install\n");
        }

        LOGGER.info("========================================");
        LOGGER.info("PRECONDITION CHECK COMPLETED SUCCESSFULLY");
        LOGGER.info("Ready to proceed with installation");
        LOGGER.info("========================================");
        return true;
    }

    /**
     * Check and cleanup a product (main or dependent)
     *
     * @param productName The product name
     * @param installInfo The product installation info
     * @param remarks     StringBuilder to add remarks
     * @param operation   The operation context
     * @return true if cleanup succeeded or no previous installation found, false if failed
     */
    private static boolean checkAndCleanupProduct(String productName, ProductInstallInfo installInfo,
                                                  StringBuilder remarks, Operation operation) {
        LOGGER.info("Checking product: " + productName);

        boolean previousInstallFound = false;
        String serviceHome = null;
        String registryEntryPath = null;

        // Step 1: Check for running service
        LOGGER.info("----------------------------------------");
        LOGGER.info("STEP 1: CHECKING FOR EXISTING SERVICE");
        LOGGER.info("----------------------------------------");

        if (installInfo.serviceName != null && !installInfo.serviceName.isEmpty()) {
            LOGGER.info("Searching for service: " + installInfo.serviceName);

            serviceHome = getServerHomeFromService(installInfo.serviceName);

            if (serviceHome != null) {
                previousInstallFound = true;
                LOGGER.info("SUCCESS - Service found");
                LOGGER.info("Service Name: " + installInfo.serviceName);
                LOGGER.info("Service Home Location: " + serviceHome);
                remarks.append("✓ Found existing service: ").append(installInfo.serviceName)
                        .append(" at location: ").append(serviceHome).append("\n");
            } else {
                LOGGER.info("RESULT - Service not found or not running");
                LOGGER.info("Service Name: " + installInfo.serviceName);
            }
        } else {
            LOGGER.info("SKIPPED - No service name configured for this product");
        }

        // Step 2: Check registry for installation
        LOGGER.info("----------------------------------------");
        LOGGER.info("STEP 2: CHECKING REGISTRY FOR INSTALLATION");
        LOGGER.info("----------------------------------------");

        if (installInfo.displayName != null && !installInfo.displayName.isEmpty()) {
            LOGGER.info("Searching registry for display name: " + installInfo.displayName);

            registryEntryPath = findProductInRegistry(productName);

            if (registryEntryPath != null) {
                previousInstallFound = true;
                LOGGER.info("SUCCESS - Registry entry found");
                LOGGER.info("Registry Path: " + registryEntryPath);
                remarks.append("✓ Found registry entry: ").append(installInfo.displayName).append("\n");

                String registryInstallPath = getInstallLocationFromRegistry(registryEntryPath);

                if (registryInstallPath != null && !registryInstallPath.isEmpty()) {
                    LOGGER.info("SUCCESS - Installation location found in registry");
                    LOGGER.info("Registry Install Location: " + registryInstallPath);
                    remarks.append("  Registry install location: ").append(registryInstallPath).append("\n");

                    if (serviceHome == null) {
                        LOGGER.info("Using registry installation path as service home");
                        serviceHome = registryInstallPath;
                    } else {
                        LOGGER.info("Service home already determined from service, using: " + serviceHome);
                    }
                } else {
                    LOGGER.warning("WARNING - Registry entry found but installation path is empty");
                    remarks.append("⚠ Registry entry found but no valid installation path\n");
                }
            } else {
                LOGGER.info("RESULT - No registry entry found");
                LOGGER.info("Display Name searched: " + installInfo.displayName);
            }
        } else {
            LOGGER.warning("SKIPPED - Display name is null or empty, cannot search registry");
        }

        // Step 3: If previous installation found, clean it up
        if (previousInstallFound) {
            LOGGER.info("========================================");
            LOGGER.info("PREVIOUS INSTALLATION DETECTED - CLEANUP REQUIRED");
            LOGGER.info("Product: " + productName);
            LOGGER.info("========================================");
            remarks.append("\n⚠ Previous installation detected for ").append(productName).append(", initiating cleanup...\n\n");

            // Try to stop service if it's running
            LOGGER.info("----------------------------------------");
            LOGGER.info("STEP 3: STOPPING SERVICE");
            LOGGER.info("----------------------------------------");

            if (installInfo.serviceName != null && !installInfo.serviceName.isEmpty()) {
                LOGGER.info("Attempting to stop service: " + installInfo.serviceName);
                boolean serviceStopped = stopService(installInfo.serviceName, remarks);

                if (serviceStopped) {
                    LOGGER.info("SUCCESS - Service stopped successfully");
                } else {
                    LOGGER.warning("WARNING - Failed to stop service: " + installInfo.serviceName);
                    LOGGER.warning("Continuing with uninstall process anyway");
                    remarks.append("⚠ Warning: Failed to stop service, continuing with uninstall\n");
                }
            } else {
                LOGGER.info("SKIPPED - No service configured to stop");
            }

            // Try to run uninstall script if we know the server home
            LOGGER.info("----------------------------------------");
            LOGGER.info("STEP 4: RUNNING UNINSTALL SCRIPT");
            LOGGER.info("----------------------------------------");

            boolean uninstallSuccess = false;
            if (serviceHome != null) {
                LOGGER.info("Service home location: " + serviceHome);

                if (installInfo.uninstallScript != null) {
                    LOGGER.info("Uninstall Autoit script path: " + installInfo.uninstallScript);
                    File uninstallScriptFile = new File(resolveHome(installInfo.uninstallScript));

                    if (uninstallScriptFile.exists() && (uninstallScriptFile.toPath().endsWith(".au3") || uninstallScriptFile.toPath().endsWith(".exe"))) {
                        LOGGER.info("Executing uninstall script from file system");
                        uninstallSuccess = runUninstallScript(serviceHome, installInfo.uninstallScript, remarks);
                    }
                }
                if (!uninstallSuccess) {
                    LOGGER.info("Uninstall script not found in file system, attempting registry-based uninstall");
                    try {
                        boolean isRegistryCleanupDone = uninstall(productName.toLowerCase(), remarks);
                        LOGGER.info("Is Registry cleanup done : " + isRegistryCleanupDone);
                        uninstallSuccess = Uninstall.uninstallation(operation);
                        LOGGER.info("Is Uninstall bat run successfully : " + uninstallSuccess);
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "FAILED - Error during registry-based uninstall: " + e.getMessage(), e);
                    }
                }

                if (uninstallSuccess) {
                    LOGGER.info("SUCCESS - Uninstall completed successfully");
                    remarks.append("✓ Uninstall script executed successfully\n");
                } else {
                    LOGGER.warning("WARNING - Uninstall script execution failed or returned errors");
                    LOGGER.warning("Continuing with registry cleanup");
                    remarks.append("⚠ Warning: Uninstall script failed, attempting manual cleanup\n");
                }
            } else {
                LOGGER.warning("SKIPPED - Cannot run uninstall script");
                if (serviceHome == null) {
                    LOGGER.warning("Reason: Service home location is unknown");
                }
                if (installInfo.uninstallScript == null) {
                    LOGGER.warning("Reason: Uninstall script path is not configured");
                }
                remarks.append("⚠ Warning: Could not determine installation location for uninstall\n");
            }

            // If we found registry entry, try to remove it
            LOGGER.info("----------------------------------------");
            LOGGER.info("STEP 5: CLEANING UP REGISTRY ENTRY");
            LOGGER.info("----------------------------------------");

            boolean registryCleanupSuccess = false;
            if (registryEntryPath != null) {
                LOGGER.info("Attempting to remove registry entry: " + registryEntryPath);
                registryCleanupSuccess = cleanupRegistryEntry(registryEntryPath, remarks);

                if (registryCleanupSuccess) {
                    LOGGER.info("SUCCESS - Registry entry cleaned up successfully");
                    remarks.append("✓ Registry entry removed successfully\n");
                } else {
                    LOGGER.warning("FAILED - Unable to clean up registry entry");
                    LOGGER.warning("Registry path: " + registryEntryPath);
                    remarks.append("✗ Failed to clean up registry entry\n");
                }
            } else {
                LOGGER.info("SKIPPED - No registry entry found for cleanup");
            }

            // Final verification that services are gone
            LOGGER.info("----------------------------------------");
            LOGGER.info("STEP 6: VERIFICATION - SERVICE REMOVAL");
            LOGGER.info("----------------------------------------");

            boolean serviceGone = true;
            if (installInfo.serviceName != null && !installInfo.serviceName.isEmpty()) {
                LOGGER.info("Verifying service removal: " + installInfo.serviceName);
                serviceGone = verifyServiceRemoved(installInfo.serviceName, remarks);

                if (serviceGone) {
                    LOGGER.info("SUCCESS - Service has been removed successfully");
                    remarks.append("✓ Service removal verified\n");
                } else {
                    LOGGER.severe("FAILED - Service still exists after uninstall");
                    LOGGER.severe("Service Name: " + installInfo.serviceName);
                    LOGGER.severe("Product: " + productName);
                    remarks.append("✗ CRITICAL: Service still exists after cleanup for product: ").append(productName).append("\n");
                    return false;
                }
            } else {
                LOGGER.info("SKIPPED - No service to verify");
            }

            // Final verification that registry entries are gone
            LOGGER.info("----------------------------------------");
            LOGGER.info("STEP 7: VERIFICATION - REGISTRY ENTRY REMOVAL");
            LOGGER.info("----------------------------------------");

            if (registryEntryPath != null && registryCleanupSuccess) {
                LOGGER.info("Verifying registry entry removal: " + registryEntryPath);
                boolean regEntryGone = verifyRegistryEntryRemoved(registryEntryPath, remarks);

                if (regEntryGone) {
                    LOGGER.info("SUCCESS - Registry entry removal verified");
                } else {
                    LOGGER.warning("WARNING - Registry entry still exists after cleanup");
                    LOGGER.warning("This is not critical, continuing with installation");
                }
            } else {
                LOGGER.info("SKIPPED - No registry entry to verify");
            }

            LOGGER.info("========================================");
            LOGGER.info("CLEANUP COMPLETED SUCCESSFULLY for product: " + productName);
            LOGGER.info("========================================");
            remarks.append("\n✓ Cleanup completed successfully for ").append(productName).append("\n");
        } else {
            LOGGER.info("========================================");
            LOGGER.info("NO PREVIOUS INSTALLATION FOUND for product: " + productName);
            LOGGER.info("========================================");
            remarks.append("✓ No previous installation found for ").append(productName).append("\n");
        }

        return true;
    }

    /**
     * Stop a Windows service
     *
     * @param serviceName Service to stop
     * @param remarks     StringBuilder to add remarks
     * @return true if service was stopped or was not running, false if failed to stop
     */
    private static boolean stopService(String serviceName, StringBuilder remarks) {
        LOGGER.info("Attempting to stop service: " + serviceName);

        // Use ServiceManagementHandler to stop the service
        Operation serviceOp = new Operation("service_actions");
        serviceOp.setParameter("action", "stop");
        serviceOp.setParameter("service_name", serviceName);

        boolean result = ServiceManagementHandler.executeOperation(serviceOp);

        if (result) {
            LOGGER.info("SUCCESS - Service stopped successfully: " + serviceName);
            remarks.append("✓ Service stopped successfully: ").append(serviceName).append("\n");
            return true;
        } else {
            LOGGER.warning("WARNING - Failed to stop service: " + serviceName);
            LOGGER.warning("Service operation remarks: " + serviceOp.getRemarks());

            // Check if the service is already stopped or doesn't exist
            Operation checkOp = new Operation("service_actions");
            checkOp.setParameter("action", "status");
            checkOp.setParameter("service_name", serviceName);

            ServiceManagementHandler.executeOperation(checkOp);
            String status = checkOp.getRemarks();

            if (status != null && (status.contains("STOPPED") ||
                    status.contains("does not exist") ||
                    status.contains("1060") ||
                    status.contains("1061"))) {
                LOGGER.info("INFO - Service is already stopped or doesn't exist: " + serviceName);
                LOGGER.info("Service status: " + status);
                remarks.append("✓ Service already stopped or doesn't exist\n");
                return true;
            }

            LOGGER.severe("FAILED - Service could not be stopped and is still running");
            LOGGER.severe("Service name: " + serviceName);
            LOGGER.severe("Status: " + status);
            remarks.append("✗ Failed to stop service: ").append(serviceName).append("\n");
            return false;
        }
    }

    /**
     * Run uninstall script
     *
     * @param serverHome      Server home directory
     * @param uninstallScript Relative path to uninstall script
     * @param remarks         StringBuilder to add remarks
     * @return true if successful, false if failed
     */
    private static boolean runUninstallScript(String serverHome, String uninstallScript, StringBuilder remarks) {
        LOGGER.info("Running uninstall script");
        LOGGER.info("Server Home: " + serverHome);
        LOGGER.info("Uninstall Script: " + uninstallScript);

        try {
            String scriptPath = Paths.get(serverHome, uninstallScript).toString();
            File scriptFile = new File(scriptPath);

            if (!scriptFile.exists()) {
                LOGGER.warning("FAILED - Uninstall script not found");
                LOGGER.warning("Script path: " + scriptPath);
                remarks.append("✗ Uninstall script not found: ").append(scriptPath).append("\n");
                return false;
            }

            LOGGER.info("Uninstall script file found: " + scriptPath);
            LOGGER.info("Starting uninstall process...");

            // Run the uninstall script with /S for silent mode if it's an executable
            ProcessBuilder processBuilder;
            if (scriptPath.toLowerCase().endsWith(".bat")) {
                LOGGER.info("Detected batch file, executing with cmd /c");
                processBuilder = new ProcessBuilder("cmd", "/c", scriptPath, "/S");
            } else if (scriptPath.toLowerCase().endsWith(".exe")) {
                LOGGER.info("Detected executable file, executing directly");
                processBuilder = new ProcessBuilder(scriptPath, "/S");
            } else {
                LOGGER.info("Unknown file type, executing with cmd /c");
                processBuilder = new ProcessBuilder("cmd", "/c", scriptPath);
            }

            // Set working directory to script's directory
            processBuilder.directory(new File(scriptFile.getParent()));
            LOGGER.info("Working directory set to: " + scriptFile.getParent());

            // Start the process
            Process process = processBuilder.start();

            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    LOGGER.info("Uninstall output: " + line);
                }
            }

            // Read error output
            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                    LOGGER.warning("Uninstall error output: " + line);
                }
            }

            // Wait for completion with timeout
            LOGGER.info("Waiting for uninstall script to complete (timeout: 5 minutes)...");
            boolean completed = process.waitFor(300, TimeUnit.SECONDS);

            if (!completed) {
                LOGGER.severe("FAILED - Uninstall script timed out");
                LOGGER.severe("Timeout duration: 5 minutes");
                remarks.append("✗ Uninstall script timed out after 5 minutes\n");
                process.destroyForcibly();
                return false;
            }

            int exitCode = process.exitValue();
            LOGGER.info("Uninstall script process completed");
            LOGGER.info("Exit code: " + exitCode);

            if (exitCode == 0) {
                LOGGER.info("SUCCESS - Uninstall script completed successfully");
                remarks.append("✓ Uninstall completed with exit code: ").append(exitCode).append("\n");
                return true;
            } else {
                LOGGER.warning("WARNING - Uninstall script returned non-zero exit code");
                LOGGER.warning("Exit code: " + exitCode);
                if (errorOutput.length() > 0) {
                    LOGGER.warning("Error output: " + errorOutput.toString().trim());
                    remarks.append("⚠ Uninstall completed with errors (exit code: ").append(exitCode).append(")\n");
                    remarks.append("  Error: ").append(errorOutput.toString().trim()).append("\n");
                }
                return false;
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "FAILED - Exception during uninstall script execution", e);
            LOGGER.severe("Error message: " + e.getMessage());
            remarks.append("✗ Error running uninstall script: ").append(e.getMessage()).append("\n");
            return false;
        }
    }

    /**
     * Remove registry entry for a product
     *
     * @param regKeyPath Registry key path to remove
     * @param remarks    StringBuilder to add remarks
     * @return true if successful, false if failed
     */
    private static boolean cleanupRegistryEntry(String regKeyPath, StringBuilder remarks) {
        LOGGER.info("Cleaning up registry entry");
        LOGGER.info("Registry Path: " + regKeyPath);

        try {
            // Create a PowerShell command that properly escapes the registry path
            String command = "powershell.exe -Command \"Remove-Item -Path '" +
                    regKeyPath.replace("\\", "\\\\") +
                    "' -Force -Recurse -ErrorAction SilentlyContinue\"";

            LOGGER.info("Executing PowerShell command to remove registry entry");
            LOGGER.info("Command: " + command);

            Process process = Runtime.getRuntime().exec(command);

            // Read output and error streams
            try (BufferedReader stdReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                String line;
                boolean hasOutput = false;
                while ((line = stdReader.readLine()) != null) {
                    hasOutput = true;
                    LOGGER.info("PowerShell output: " + line);
                }
                if (!hasOutput) {
                    LOGGER.info("No output from PowerShell command");
                }

                boolean hasErrors = false;
                while ((line = errReader.readLine()) != null) {
                    LOGGER.warning("PowerShell error: " + line);
                    hasErrors = true;
                }

                int exitCode = process.waitFor();
                LOGGER.info("PowerShell command completed");
                LOGGER.info("Exit code: " + exitCode);

                if (hasErrors) {
                    LOGGER.warning("PowerShell reported errors during registry cleanup");
                }

                // Verify registry entry was removed
                LOGGER.info("Verifying registry entry removal...");
                boolean verified = verifyRegistryEntryRemoved(regKeyPath, remarks);

                if (verified) {
                    LOGGER.info("SUCCESS - Registry entry successfully removed and verified");
                    remarks.append("✓ Registry entry removed and verified\n");
                    return true;
                } else {
                    LOGGER.warning("FAILED - Registry entry still exists after cleanup attempt");
                    LOGGER.warning("Registry path: " + regKeyPath);
                    remarks.append("✗ Failed to remove registry entry\n");
                    return false;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "FAILED - Exception while removing registry entry", e);
            LOGGER.severe("Registry path: " + regKeyPath);
            LOGGER.severe("Error message: " + e.getMessage());
            remarks.append("✗ Error removing registry entry: ").append(e.getMessage()).append("\n");
            return false;
        }
    }

    /**
     * Verify that a service has been removed
     *
     * @param serviceName Name of the service
     * @param remarks     StringBuilder to add remarks
     * @return true if service does not exist, false if still exists
     */
    private static boolean verifyServiceRemoved(String serviceName, StringBuilder remarks) {
        LOGGER.info("Verifying service removal");
        LOGGER.info("Service Name: " + serviceName);

        try {
            // Use ServiceManagementHandler to check service status
            Operation serviceOp = new Operation("service_actions");
            serviceOp.setParameter("action", "status");
            serviceOp.setParameter("service_name", serviceName);

            ServiceManagementHandler.executeOperation(serviceOp);
            String status = serviceOp.getRemarks();

            LOGGER.info("Service status check completed");
            LOGGER.info("Status: " + (status != null ? status : "null"));

            if (status != null && (status.contains("does not exist") ||
                    status.contains("1060") ||
                    status.contains("1061"))) {
                LOGGER.info("SUCCESS - Service has been successfully removed");
                LOGGER.info("Service name: " + serviceName);
                remarks.append("✓ Service successfully removed and verified\n");
                return true;
            }

            LOGGER.warning("FAILED - Service still exists");
            LOGGER.warning("Service name: " + serviceName);
            LOGGER.warning("Current status: " + status);
            remarks.append("✗ Service still exists: ").append(status).append("\n");
            return false;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "FAILED - Exception during service verification", e);
            LOGGER.severe("Service name: " + serviceName);
            LOGGER.severe("Error message: " + e.getMessage());
            remarks.append("✗ Error verifying service removal: ").append(e.getMessage()).append("\n");
            return false;
        }
    }

    /**
     * Verify that a registry entry has been removed
     *
     * @param regKeyPath Registry key path
     * @param remarks    StringBuilder to add remarks
     * @return true if registry entry does not exist, false if still exists
     */
    private static boolean verifyRegistryEntryRemoved(String regKeyPath, StringBuilder remarks) {
        LOGGER.info("Verifying registry entry removal");
        LOGGER.info("Registry Path: " + regKeyPath);

        try {
            // Check if the key still exists
            ProcessBuilder checkBuilder = new ProcessBuilder("reg", "query", regKeyPath);
            Process checkProcess = checkBuilder.start();
            int checkResult = checkProcess.waitFor();

            if (checkResult != 0) {
                // Exit code non-zero means the key was not found - good!
                LOGGER.info("SUCCESS - Registry entry has been successfully removed");
                LOGGER.info("Registry path: " + regKeyPath);
                remarks.append("✓ Registry entry successfully removed\n");
                return true;
            } else {
                // Key still exists
                LOGGER.warning("WARNING - Registry entry still exists");
                LOGGER.warning("Registry path: " + regKeyPath);
                remarks.append("⚠ Registry entry still exists: ").append(regKeyPath).append("\n");
                return false;
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "FAILED - Error verifying registry entry removal", e);
            LOGGER.warning("Registry path: " + regKeyPath);
            LOGGER.warning("Error message: " + e.getMessage());
            remarks.append("✗ Error verifying registry entry removal: ").append(e.getMessage()).append("\n");
            return false;
        }
    }

    /**
     * Get server home directory from a service
     *
     * @param serviceName Name of the service to check
     * @return Server home directory or null if not found
     */
    public static String getServerHomeFromService(String serviceName) {
        LOGGER.info("Getting server home from service");
        LOGGER.info("Service Name: " + serviceName);

        try {
            ProcessBuilder processBuilder = new ProcessBuilder("sc", "qc", serviceName);
            Process process = processBuilder.start();

            // Read all output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            process.waitFor();

            // Parse output to find BINARY_PATH_NAME
            String processOutput = output.toString();

            if (processOutput.contains("The specified service does not exist")) {
                LOGGER.info("RESULT - Service does not exist");
                return null;
            }

            // Extract BINARY_PATH_NAME
            Pattern pattern = Pattern.compile("BINARY_PATH_NAME\\s*:\\s*([^\\n]*)");
            Matcher matcher = pattern.matcher(processOutput);

            if (matcher.find()) {
                String binaryPath = matcher.group(1).trim();
                LOGGER.info("Found BINARY_PATH_NAME: " + binaryPath);

                // Extract server home from binary path
                int binIndex = binaryPath.toLowerCase().indexOf("bin\\");
                if (binIndex > 0) {
                    String serverHome = binaryPath.substring(0, binIndex);
                    serverHome = serverHome.replaceAll("^\"|\"$", "");
                    LOGGER.info("SUCCESS - Extracted server home path: " + serverHome);
                    return serverHome;
                }

                // Alternative: If wrapper.exe not found, try to extract from conf path
                int confIndex = binaryPath.toLowerCase().indexOf("conf\\wrapper.conf");
                if (confIndex > 0) {
                    String serverHome = binaryPath.substring(0, confIndex);
                    serverHome = serverHome.replaceAll("^\"|\"$", "");
                    LOGGER.info("SUCCESS - Extracted server home path from conf: " + serverHome);
                    return serverHome;
                }
            }

            LOGGER.warning("WARNING - Could not extract server home from service binary path");
            return null;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "FAILED - Error getting server home from service: " + serviceName, e);
            LOGGER.warning("Error message: " + e.getMessage());
            return null;
        }
    }

    /**
     * Find product in registry by product name
     *
     * @param productName Product name to search for
     * @return Registry path if found, null otherwise
     */
    private static String findProductInRegistry(String productName) {
        LOGGER.info("Searching registry for product");
        LOGGER.info("Product Name: " + productName);

        try {
            // Step 1: Get registry codes for this product type
            String[] registryCodes = getRegistryCodesFromConfig(productName);

            if (registryCodes != null && registryCodes.length > 0) {
                LOGGER.info("Found " + registryCodes.length + " registry codes to search");

                // Step 2: Search for each registry code
                for (String code : registryCodes) {
                    LOGGER.info("Searching for registry code: " + code);
                    String regPath = "HKLM\\SOFTWARE\\Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\{" + code + "}";
                    String regCmd = "reg query \"" + regPath + "\"";

                    String regOutput = Uninstall.runCommand(regCmd);
                    if (regOutput != null && !regOutput.contains("ERROR:") && regOutput.contains(code)) {
                        LOGGER.info("SUCCESS - Found registry entry for code: " + code);
                        return "HKEY_LOCAL_MACHINE\\SOFTWARE\\Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\{" + code + "}";
                    }
                }
            } else {
                LOGGER.info("No registry codes found in configuration");
            }

            LOGGER.info("RESULT - No matching registry entry found");
            return null;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "FAILED - Error searching registry", e);
            LOGGER.severe("Error message: " + e.getMessage());
            return null;
        }
    }

    /**
     * Gets registry codes for a product from the configuration file
     *
     * @param productType Product type to look up
     * @return Array of registry codes or null if not found
     */
    private static String[] getRegistryCodesFromConfig(String productType) {
        LOGGER.info("Reading registry codes from configuration");
        LOGGER.info("Product Type: " + productType);

        try {
            String productTypeLower = productType.toLowerCase();
            String configFilePath = getToolServerHome() + PRODUCT_SETUP_CONF;
            Path path = Paths.get(configFilePath);
            String jsonContent = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

            JSONObject jsonObject = new JSONObject(jsonContent);
            if (!jsonObject.has(productTypeLower)) {
                LOGGER.info("Product type not found in configuration: " + productTypeLower);
                return null;
            }

            JSONObject productConfig = jsonObject.getJSONObject(productTypeLower);
            if (!productConfig.has("registry_code")) {
                LOGGER.info("No registry_code found for product: " + productTypeLower);
                return null;
            }

            String codesStr = productConfig.getString("registry_code");
            String[] codes = codesStr.split(",");
            for (int i = 0; i < codes.length; i++) {
                codes[i] = codes[i].trim();
            }
            LOGGER.info("SUCCESS - Extracted " + codes.length + " registry codes");

            return codes;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "FAILED - Error reading registry codes from config", e);
            LOGGER.severe("Error message: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get installation location from registry entry
     *
     * @param regKeyPath Registry key path
     * @return Installation location or null if not found
     */
    private static String getInstallLocationFromRegistry(String regKeyPath) {
        LOGGER.info("Getting install location from registry");
        LOGGER.info("Registry Path: " + regKeyPath);

        try {
            ProcessBuilder processBuilder = new ProcessBuilder("reg", "query", regKeyPath, "/v", "InstallLocation");
            Process process = processBuilder.start();

            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            process.waitFor();

            // Parse output
            String processOutput = output.toString();

            // Extract InstallLocation value
            Pattern pattern = Pattern.compile("InstallLocation\\s+REG_[^\\s]+\\s+([^\\n]+)");
            Matcher matcher = pattern.matcher(processOutput);

            if (matcher.find()) {
                String location = matcher.group(1).trim();
                LOGGER.info("SUCCESS - Found install location: " + location);
                return location;
            }

            LOGGER.info("RESULT - Install location not found in registry");
            return null;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "FAILED - Error getting install location from registry: " + regKeyPath, e);
            LOGGER.warning("Error message: " + e.getMessage());
            return null;
        }
    }
}
