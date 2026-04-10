package com.me.util;

import com.me.Operation;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.me.testcases.ExeInstall.DEFAULT_AUTOIT_DIR;
import static com.me.testcases.ExeInstall.DEFAULT_SCRIPTS_DIR;
import static com.me.testcases.ServerUtils.getToolServerHome;
import static com.me.util.GOATCommonConstants.PRODUCT_SETUP_CONF;

public class Uninstall {

    private static final Logger LOGGER = LogManager.getLogger(Uninstall.class, LogManager.LOG_TYPE.FW);
    private static String productName;

    public static boolean uninstallation(Operation operation) {
        try {
            LOGGER.log(Level.INFO, "Inside Uninstall the existing server");

            // Load service list from JSON file
            Map<String, String[]> serviceList = loadServiceListFromJson();

            LOGGER.info("Pre Uninstaller Method Called ...!!!");
//            LOGGER.info("Product Name: " + findProductName(serverHome));
            if (!serviceList.isEmpty()) {
                String[] services = serviceList.get(operation.getParameter("product_name").toLowerCase());
                for (String service : services) {
                    int servicestatus = checkService(service);
                    if (servicestatus != 0) {
                        LOGGER.info(service + " exist");
                        String installationBinDIR = getServiceDir(service);
                        LOGGER.info("Installation DIR: " + installationBinDIR);
                        uninstall(installationBinDIR, operation);
                    } else {
                        LOGGER.info(service + " Service is not installed");
                    }
                }
            } else {
                LOGGER.info("There is no service list found in product-setup.JSON, so skip the service removing step");
            }
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.INFO,"Exception while uninstall the existing server : {0}",e);
        }
        return false;
    }

    /**
     * Load service list from JSON file
     */
    private static Map<String, String[]> loadServiceListFromJson() {
        Map<String, String[]> serviceList = new HashMap<>();
        String jsonFilePath = getToolServerHome() + PRODUCT_SETUP_CONF;

        try {
            // Check if file exists
            File jsonFile = new File(jsonFilePath);
            if (!jsonFile.exists()) {
                LOGGER.warning("Service list JSON file not found: " + jsonFilePath);
                return null;
            }

            // Read JSON content
            String jsonContent = new String(Files.readAllBytes(Paths.get(jsonFilePath)), StandardCharsets.UTF_8);

            // Parse JSON using org.json
            JSONObject jsonObject = new JSONObject(jsonContent);

            // Process each product entry (endpointcentral, agent, etc.)
            for (String productKey : jsonObject.keySet()) {
                JSONObject productData = jsonObject.getJSONObject(productKey);
                List<String> serviceNames = new ArrayList<>();

                // Add main product's service name
//                if (productData.has("servicename")) {
//                    serviceNames.add(productData.getString("servicename"));
//                }

                // Add service names from dependent products
                if (productData.has("depended_product")) {
                    JSONArray dependedProducts = productData.getJSONArray("depended_product");

                    for (int i = 0; i < dependedProducts.length(); i++) {
                        String dependedProductKey = dependedProducts.getString(i);

                        // If the dependent product exists in our JSON
                        if (jsonObject.has(dependedProductKey)) {
                            JSONObject dependedProductData = jsonObject.getJSONObject(dependedProductKey);
                            if (dependedProductData.has("servicename")) {
                                serviceNames.add(dependedProductData.getString("servicename"));
                            }
                        } else {
                            // For cases where the dependent product might be directly a service name
                            serviceNames.add(dependedProductKey);
                        }
                    }
                }

                // Convert list to array and add to map
                if (!serviceNames.isEmpty()) {
                    // Remove duplicates
                    List<String> uniqueServices = new ArrayList<>(new LinkedHashSet<>(serviceNames));
                    String[] services = uniqueServices.toArray(new String[0]);
                    serviceList.put(productKey.toLowerCase(), services);
                    LOGGER.info("Loaded services for " + productKey + ": " + String.join(", ", services));
                }
            }

            if (serviceList.isEmpty()) {
                LOGGER.warning("No service lists found in product setup JSON");
                return null;
            }

            return serviceList;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading service list from JSON: " + jsonFilePath, e);
            LOGGER.log(Level.INFO, "Using default service list");
            return null;
        }
    }

    public  static  String findProductName(String serverHomeDir) {
        File confFile;
        try {
            if(serverHomeDir != null) {

                confFile = new File(serverHomeDir + File.separator + "conf" + File.separator + "product.conf");
                Properties prodProp = new Properties();
                prodProp.load(new FileReader(confFile));
                return prodProp.getProperty("activeproductcodes");
            }
        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE,"Exception: ",e);
        }
        return "NOT_FOUND";
    }

//    public static String getServiceDir(String serviceName) {
//        try {
//            String scOutput = runCommand("sc qc \"" + serviceName + "\"");
//            String binaryPath = extractBinaryPathName(scOutput);
//
//            if (binaryPath.isEmpty()) {
//                LOGGER.info("Could not extract binary path for " + serviceName);
//                return "";
//            }
//
//            // Remove quotes if present
//            binaryPath = binaryPath.replace("\"", "");
//
//            // Get the directory by removing the file name
//            File file = new File(binaryPath);
//            String binDir = file.getParent();
//
//            // Ensure it ends at the bin directory
//            if (binDir.toLowerCase().endsWith("\\bin")) {
//                return binDir;
//            } else {
//                // Try to find bin directory
//                int binIndex = binDir.toLowerCase().indexOf("\\bin\\");
//                if (binIndex != -1) {
//                    return binDir.substring(0, binIndex + 5) + File.separator;
//                }
//            }
//
//            return binDir;
//        } catch (Exception e) {
//            LOGGER.log(Level.SEVERE, "Error getting service directory", e);
//            return "";
//        }
//    }

    public static String getServiceDir(String serviceName) {
        try {
            String scOutput = runCommand("sc qc \"" + serviceName + "\"");
            String binaryPath = extractBinaryPathName(scOutput);

            if (binaryPath.isEmpty()) {
                LOGGER.info("Could not extract binary path for " + serviceName);
                return "";
            }

            // Remove quotes if present
            binaryPath = binaryPath.replace("\"", "");

            // Extract just the executable path (before any arguments)
            int spaceIndex = binaryPath.indexOf(" -");
            if (spaceIndex > 0) {
                binaryPath = binaryPath.substring(0, spaceIndex);
            }

            // Get the directory by removing the file name
            File file = new File(binaryPath);
            String binDir = file.getParent();

            // Add file separator if missing
            if (!binDir.endsWith(File.separator)) {
                binDir += File.separator;
            }

            return binDir;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting service directory", e);
            return "";
        }
    }


    public static String extractBinaryPathName(String scOutput) {
        if (scOutput == null || scOutput.isEmpty()) {
            return "";
        }

        // Find the line containing BINARY_PATH_NAME
        String[] lines = scOutput.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("BINARY_PATH_NAME")) {
                // Extract everything after the colon and trim
                int colonIndex = line.indexOf(':');
                if (colonIndex != -1) {
                    return line.substring(colonIndex + 1).trim();
                }
            }
        }

        return "";
    }

//    public static void uninstall(String installationBinDIR) {
//        Path uninstallBat = Paths.get(installationBinDIR).resolve("scripts").resolve("uninstall.bat");//No I18N
//        if (uninstallBat.toFile().exists()) {
//            LOGGER.log(Level.INFO,"uninstall bat called..");
//            ProcessBuilder builder = new ProcessBuilder(String.valueOf(uninstallBat));
//            builder.redirectErrorStream(true);
//            Process process = null;
//            try {
//                process = builder.start();
//                long timeout = 600000; // 10 minutes timeout
//                boolean processExited = process.waitFor(timeout, TimeUnit.MILLISECONDS);
//                if (!processExited) {
//                    process.destroyForcibly();// Process did not exit within timeout, forcibly destroy it
//                    LOGGER.log(Level.INFO, "Process forcibly destroyed.");
//                } else {
//                    LOGGER.log(Level.INFO, String.valueOf(process.exitValue()));// Process exited normally within timeout
//                }
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//            catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//            finally {
//                if (process != null && process.isAlive()) {
//                    process.destroyForcibly();
//                }
//
//            }
//        }
//    }


    public static void uninstall(String installationBinDIR,Operation op) {
        Path uninstallBat = Paths.get(installationBinDIR).resolve("scripts").resolve("uninstall.bat");
        if (uninstallBat.toFile().exists()) {
            LOGGER.log(Level.INFO, "Running uninstall bat with elevation...");
            try {
                // Create PowerShell command to run batch file with elevation
                String command = "powershell.exe -Command \"Start-Process -FilePath '" +
                        uninstallBat.toString().replace("\\", "\\\\") +
                        "' -Verb RunAs -Wait\"";

                ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", command);
                builder.redirectErrorStream(true);
                Process process = builder.start();

                // Read output stream
//                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
//                    String line;
//                    while ((line = reader.readLine()) != null) {
//                        LOGGER.info(line);
//                    }
//                }

                boolean completed = process.waitFor(2, TimeUnit.MINUTES);
                if (!completed) {
                    String autoItDir = op.getParameter("autoit_dir");
                    String baseDir = op.getParameter("base_dir");
                    String scriptsDir = op.getParameter("scripts_dir");

                    if (baseDir == null || baseDir.isEmpty()) {
                        baseDir = System.getProperty("user.dir");
                    }

                    if (scriptsDir == null || scriptsDir.isEmpty()) {
                        scriptsDir = Paths.get(baseDir, DEFAULT_SCRIPTS_DIR).toString();
                    }

                    if (autoItDir == null || autoItDir.isEmpty()) {
                        autoItDir = Paths.get(baseDir, DEFAULT_AUTOIT_DIR).toString();
                    }

                    if (!autoItDir.isEmpty() && autoItDir != null  && !scriptsDir.isEmpty() && scriptsDir != null) {
                        LOGGER.log(Level.INFO," AutoIT script called for UAC");
                        uacAutoIT(autoItDir, scriptsDir);
                    }

                    process.destroyForcibly();
                    LOGGER.warning("Uninstall process timed out and was terminated");
                } else {
                    LOGGER.info("Uninstall completed with exit code: " + process.exitValue());
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error during uninstallation", e);
            }
        } else {
            LOGGER.warning("Uninstall script not found at: " + uninstallBat);
            op.setRemarks("Uninstall script not found at: " + uninstallBat);
            op.setRemarks("So unable to uninstall the product");
        }
    }

    public static boolean uacAutoIT(String autoItPath, String scriptsPath) {
        try {
            String autoItExe = Paths.get(autoItPath, "AutoIt3.exe").toString();
            String scriptFile = Paths.get(scriptsPath, "runAaAdmin_Confirmation_PopUp.au3").toString();
            String command = autoItExe + " " + scriptFile;
            LOGGER.info("AutoIT executable path: " + autoItExe);
            LOGGER.info("AutoIT script path: " + scriptFile);
            LOGGER.info("Executing AutoIT script: " + command);

            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", command);
            builder.redirectErrorStream(true);
            Process process = builder.start();

            boolean completed = process.waitFor(2, TimeUnit.MINUTES);
            if (!completed) {
                LOGGER.warning("AutoIT process timed out and was terminated");
                return false;
            } else {
                LOGGER.info("AutoIT script executed with exit code: " + process.exitValue());
                return true;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during AutoIT execution for UAC", e);
            return false;
        }

    }


    public static int checkService(String serviceName) {
        try {
            String scOutput = runCommand(System.getenv("WINDIR")+"\\System32\\sc.exe" + " query " + serviceName);

            if (scOutput.contains("STATE")) {
                if (scOutput.contains("RUNNING")) {
                    return 2;           // Installed and in running status
                } else {
                    return 1;          // Installed but not in running status
                }
            } else {
                return 0;             // Unknown Service
            }
        } catch (IOException e) {
            return 0;
        }
    }

    public static String runCommand(String command) throws IOException {
        LOGGER.info("Executing command: " + command);
        Process process = Runtime.getRuntime().exec(command);
        InputStream is = process.getInputStream();
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        String line;
        String scOutput = "";

        while ((line = br.readLine()) != null) {
            scOutput +=  line + "\n" ;
        }
        LOGGER.info("output: "+scOutput);
        return scOutput;
    }


    /**
     * Uninstalls a product by removing its registry entries
     *
     * @param productName The name of the product to uninstall
     * @param remarks StringBuilder to collect remarks/logs (may be null)
     * @return true if successful, false otherwise
     */
    public static boolean uninstall(String productName, StringBuilder remarks) {
        try {
            LOGGER.info("Processing uninstall for product: " + productName);
//            if (remarks != null) {
//                remarks.append("Starting uninstall process for product: ").append(productName).append("\n");
//            }

            // Get registry codes from product configuration
            String[] registryCodes = getRegistryCodesFromConfig(productName);

            if (registryCodes == null || registryCodes.length == 0) {
                LOGGER.warning("No registry codes found for product: " + productName);
//                if (remarks != null) {
//                    remarks.append("No registry codes found for product: ").append(productName).append("\n");
//                }
                return false;
            }

            LOGGER.info("Found " + registryCodes.length + " registry codes for product: " + productName);
            boolean anyRemoved = false;

            for (String code : registryCodes) {
                LOGGER.info("Processing registry code: " + code);

                // Build proper PowerShell command with correct formatting of GUIDs
                String[] command = {
                        "powershell.exe",
                        String.format(
                                "Remove-Item -Path 'HKLM:\\SOFTWARE\\Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\{%s}' -Force -ErrorAction SilentlyContinue; " +
                                        "Remove-Item -Path 'HKLM:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\{%s}' -Force -ErrorAction SilentlyContinue",
                                code, code
                        )
                };

                LOGGER.info("Executing PowerShell command to remove registry entries");
                LOGGER.info("Command: " + String.join(" ", command));
                Process process = Runtime.getRuntime().exec(command);

                // Read PowerShell output
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        LOGGER.info("PowerShell output: " + line);
                    }
                }

                // Read PowerShell errors
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        LOGGER.info("PowerShell output: " + line);
                    }
                }

                int exitCode = process.waitFor();
                LOGGER.info("PowerShell command completed with exit code: " + exitCode);

                if (exitCode == 0) {
                    anyRemoved = true;
//                    if (remarks != null) {
//                        remarks.append("Successfully removed registry entries for code: ").append(code).append("\n");
//                    }
                } else {
                    LOGGER.warning("PowerShell command failed with exit code: " + exitCode);
//                    if (remarks != null) {
//                        remarks.append("Failed to remove registry entries for code: ").append(code).append("\n");
//                    }
                }
            }

            return anyRemoved;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during uninstall process", e);
            if (remarks != null) {
                remarks.append("Error during uninstall process: ").append(e.getMessage()).append("\n");
            }
            return false;
        }
    }


    /**
     * Gets registry codes for a product from the configuration file
     *
     * @param productName Name of the product (e.g., "endpointcentral")
     * @return Array of registry codes or empty array if none found
     */
    private static String[] getRegistryCodesFromConfig(String productName) {
        LOGGER.info("Starting getRegistryCodesFromConfig for product: " + productName);
        String configFilePath = getToolServerHome() + PRODUCT_SETUP_CONF;
        LOGGER.info("Configuration file path: " + configFilePath);

        try {
            File configFile = new File(configFilePath);
            if (!configFile.exists()) {
                LOGGER.severe("Configuration file does not exist: " + configFilePath);
                return new String[0];
            }

            String jsonContent = new String(Files.readAllBytes(Paths.get(configFilePath)), StandardCharsets.UTF_8);
            JSONObject jsonObject = new JSONObject(jsonContent);

            // Find main product key (case-insensitive)
            String matchedProduct = null;
            for (String key : jsonObject.keySet()) {
                if (key.equalsIgnoreCase(productName)) {
                    matchedProduct = key;
                    break;
                }
            }
            if (matchedProduct == null) {
                LOGGER.warning("Product not found in configuration: " + productName);
                return new String[0];
            }

            JSONObject productConfig = jsonObject.getJSONObject(matchedProduct);
            Set<String> registryCodes = new LinkedHashSet<>();

            // Add main product registry code
            if (productConfig.has("registry_code")) {
                registryCodes.add(productConfig.getString("registry_code"));
            }

            // Check for depended products
            if (productConfig.has("depended_product")) {
                JSONArray dependedProducts = productConfig.getJSONArray("depended_product");
                for (int i = 0; i < dependedProducts.length(); i++) {
                    String dependedKey = dependedProducts.getString(i);
                    if (jsonObject.has(dependedKey)) {
                        JSONObject dependedConfig = jsonObject.getJSONObject(dependedKey);
                        if (dependedConfig.has("registry_code")) {
                            registryCodes.add(dependedConfig.getString("registry_code"));
                        }
                    }
                }
            }

            LOGGER.info("Returning " + registryCodes.size() + " registry codes for product: " + productName);
            return registryCodes.toArray(new String[0]);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error parsing product configuration data", e);
            return new String[0];
        }
    }



    public static String getMatchedStr(String patternStr, String searchStr) {
        String matchedStr = "";
        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(searchStr);
        while (matcher.find()) {
            matchedStr = matcher.group(0);
        }
        return matchedStr;
    }
}
