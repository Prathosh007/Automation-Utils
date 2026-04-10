package com.me.testcases;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.me.util.GOATCommonConstants;
import com.me.util.LogManager;
import com.adventnet.mfw.ConsoleOut;

import static com.me.util.GOATCommonConstants.SERVERHOMEMAP;
import static com.me.util.GOATCommonConstants.TEST_SETUP_DETAILES;

/**
 * Utility class for server path operations and server home resolution
 */
public class ServerUtils {
    
    private static final Logger LOGGER = LogManager.getLogger(ServerUtils.class, LogManager.LOG_TYPE.FW);
    
    // Cache for server home paths to avoid repeated lookups
//    private static String cachedProductServerHome = null;
    public static String cachedToolServerHome = null;
    
    /**
     * Get the server home directory path
     * 
     * @return The resolved server home path
     * @deprecated Use getProductServerHome() or getToolServerHome() for clarity
     */
    @Deprecated
    public static String getServerHomePath() {
        return getProductServerHome();
    }
    
    /**
     * Get the server home directory path for the product
     * 
     * @return The resolved server home path
     */
    public static String getProductServerHome() {
        // Return cached value if already calculated
//        if (cachedProductServerHome != null) {
//            return cachedProductServerHome;
//        }
        
        // First check if set as system property
        String serverHome;
//        if (serverHome != null && !serverHome.isEmpty()) {
////            cachedProductServerHome = serverHome;
//            LOGGER.info("Using product.server.home system property: " + serverHome);
//            return serverHome;
//        }
        
        // Try to find the server directory using various methods
        serverHome = findServerDirectory();
        if(serverHome != null) {
            System.setProperty("server.home", serverHome);

            // Cache and return the result
//        cachedProductServerHome = serverHome;
            LOGGER.info("Resolved product server home: " + serverHome);
        }
        return serverHome;
    }
    
    /**
     * Get the tool's server home directory path
     * 
     * @return The G.O.A.T tool's server home path
     */
    public static String getToolServerHome() {
        // Return cached value if already calculated
        if (cachedToolServerHome != null) {
            return cachedToolServerHome;
        }
        
        // First check if set as system property
        String toolServerHome = System.getProperty("tool.server.home");
        if (toolServerHome != null && !toolServerHome.isEmpty()) {
            cachedToolServerHome = toolServerHome;
            LOGGER.info("Using tool.server.home system property: " + toolServerHome);
            return toolServerHome;
        }
        
        // Calculate tool home based on current execution context
        toolServerHome = findToolDirectory();
        
        // Cache and return the result
        cachedToolServerHome = toolServerHome;
        LOGGER.info("Resolved tool server home: " + toolServerHome);
        return toolServerHome;
    }
    
    /**
     * Find the server directory using various detection methods
     */
    private static String findServerDirectory() {
        String serverHome;
        
        // Try to get server home from service first (highest priority)
        serverHome = getServerHomeFromService();
        if (serverHome != null && !serverHome.isEmpty()) {
            LOGGER.info("Using server home from service: " + serverHome);
            return serverHome;
        }
        
//        // Method 1: Try to find ServerDirectory under the GOAT bin folder
//        try {
//            String binPath = System.getProperty("user.dir");
//            if (binPath != null) {
//                // If we're in product_package/bin
//                if (binPath.endsWith("bin") || binPath.endsWith("bin/") || binPath.endsWith("bin\\")) {
//                    File serverDir = new File(binPath, "ServerDirectory");
//                    if (serverDir.exists() && serverDir.isDirectory()) {
//                        serverHome = findLatestServerSubdirectory(serverDir.getAbsolutePath());
//                        if (serverHome != null) {
//                            return serverHome;
//                        }
//                    }
//                }
//
//                // If we're in g.o.a.t or one level down
//                File productPackageBin = new File(binPath, "product_package/bin");
//                if (productPackageBin.exists()) {
//                    File serverDir = new File(productPackageBin, "ServerDirectory");
//                    if (serverDir.exists() && serverDir.isDirectory()) {
//                        serverHome = findLatestServerSubdirectory(serverDir.getAbsolutePath());
//                        if (serverHome != null) {
//                            return serverHome;
//                        }
//                    }
//                }
//
//                // Try going up one directory level
//                File parentDir = new File(binPath).getParentFile();
//                if (parentDir != null) {
//                    File serverDir = new File(parentDir, "ServerDirectory");
//                    if (serverDir.exists() && serverDir.isDirectory()) {
//                        serverHome = findLatestServerSubdirectory(serverDir.getAbsolutePath());
//                        if (serverHome != null) {
//                            return serverHome;
//                        }
//                    }
//                }
//            }
//        } catch (Exception e) {
//            LOGGER.log(Level.WARNING, "Error finding server directory relative to bin", e);//NO I18N
//        }
//
//        // Method 2: Try standard installation paths
//        String[] possiblePaths = {
//            "D:/git/g.o.a.t/product_package/bin/ServerDirectory",//NO I18N
//            "./ServerDirectory",//NO I18N
//            "../ServerDirectory",//NO I18N
//            "../../product_package/bin/ServerDirectory",//NO I18N
//            "ServerDirectory"//NO I18N
//        };
//
//        for (String path : possiblePaths) {
//            File serverDir = new File(path);
//            if (serverDir.exists() && serverDir.isDirectory()) {
//                serverHome = findLatestServerSubdirectory(serverDir.getAbsolutePath());
//                if (serverHome != null) {
//                    return serverHome;
//                }
//            }
//        }
//
//        // Method 3: If all else fails, use the working directory as a fallback
//        LOGGER.warning("Could not find server directory, using current directory as fallback");//NO I18N
//        return System.getProperty("user.dir");//NO I18N
        return serverHome;
    }
    
    /**
     * Find the tool directory based on execution context
     */
    private static String findToolDirectory() {
        // Method 1: Use user.dir (current working directory)
        String currentDir = System.getProperty("user.dir");//NO I18N
        if (currentDir != null) {
            // Look for key directories that would indicate we're in the tool root
            File sourceDir = new File(currentDir, "product_package");//NO I18N
            if (sourceDir.exists() && sourceDir.isDirectory()) {
                return currentDir;
            }
            
            // If we're in bin directory
            if (currentDir.endsWith("bin") || currentDir.endsWith("bin/") || currentDir.endsWith("bin\\")) {//NO I18N
                File parent = new File(currentDir).getParentFile();
                if (parent != null) {
                    File parentParent = parent.getParentFile();
                    if (parentParent != null) {
                        File sourceInParentParent = new File(parentParent, "product_package");//NO I18N
                        if (sourceInParentParent.exists()) {
                            return parentParent.getAbsolutePath();
                        }
                    }
                }
            }
            
            // Try up one level
            File parent = new File(currentDir).getParentFile();
            if (parent != null) {
                File sourceInParent = new File(parent, "product_package");//NO I18N
                if (sourceInParent.exists()) {
                    return parent.getAbsolutePath();
                }
            }
        }
        
        // Method 2: Use tool specific paths/structure
        // Find out class location
        String classPath = ServerUtils.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        if (classPath != null && !classPath.isEmpty()) {
            File classFile = new File(classPath);
            // Navigate upward until we find a directory structure that looks like our tool
            File dir = classFile.getParentFile();
            while (dir != null) {
                if (new File(dir, "product_package").exists()) {//NO I18N
                    return dir.getAbsolutePath();
                }
                dir = dir.getParentFile();
            }
        }
        
        // Method 3: Fallback to current directory with warning
        LOGGER.warning("Could not determine tool directory, using current directory");//NO I18N
        return currentDir;
    }
    
    /**
     * Find the most recent server directory based on timestamp pattern
     * 
     * @param basePath Base path to search in
     * @return Path to the latest server directory, or null if none found
     */
    private static String findLatestServerSubdirectory(String basePath) {
        File baseDir = new File(basePath);
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            return null;
        }
        
        // List all subdirectories and sort by name descending
        File[] subdirs = baseDir.listFiles(File::isDirectory);
        if (subdirs == null || subdirs.length == 0) {
            return null;
        }
        
        Arrays.sort(subdirs, (a, b) -> b.getName().compareTo(a.getName()));
        
        // Look for server structure in the most recent directory
        for (File dir : subdirs) {
            // If this directory contains UEMS_CentralServer, use that
            File centralServer = new File(dir, "UEMS_CentralServer");//NO I18N
            if (centralServer.exists() && centralServer.isDirectory()) {
                return centralServer.getAbsolutePath();
            }
        }
        
        // If no UEMS_CentralServer found, return the most recent directory
        return subdirs[0].getAbsolutePath();
    }
    
    /**
     * Get the appropriate server home path based on context
     * 
     * @param context The context requesting the server home (product, tool, or test)
     * @return The appropriate server home path for the context
     */
    public static String getServerHomeForContext(String context) {
        if (context == null) {
            return getProductServerHome(); // Default to product server home
        }
        
        switch (context.toLowerCase()) {
            case "product":
                return getProductServerHome();
            case "tool":
                return getToolServerHome();
            case "test":
                // For tests, use the product server home
                return getProductServerHome();
            default:
                return getProductServerHome();
        }
    }
    
    /**
     * Set up server home system property based on the current operation context
     * 
     * @param context The context to use (product, tool, or test)
     */
    public static void setupServerHomeProperty(String context) {
        String serverHome = getServerHomeForContext(context);
        System.setProperty("server.home", serverHome);
        LOGGER.info("Set server.home system property for context '" + context + "': " + serverHome);//NO I18N
        
        // Also set context-specific properties for clarity
        if ("product".equalsIgnoreCase(context)) {
            System.setProperty("product.server.home", serverHome);//NO I18N
        } else if ("tool".equalsIgnoreCase(context)) {
            System.setProperty("tool.server.home", serverHome);//NO I18N
        }
    }

    public static boolean checkServerHome(String testId ){
        if (!SERVERHOMEMAP.containsKey(testId+"_ServerHome")) {
            LOGGER.warning("Server home not found for test ID: " + testId);//No I18N
            String serverHome = getProductServerHome();
            if (serverHome == null){
                return false;
            }
            SERVERHOMEMAP.put(testId+"_ServerHome", serverHome);//No I18N
            System.setProperty("product.server.home", serverHome);//No I18N
            return true;
        }
        return false;
    }

    public static String getServerHome(String testId ){
        if (!SERVERHOMEMAP.containsKey(testId+"_ServerHome")) {
            LOGGER.warning("Server home not found for test ID: " + testId);//No I18N
            String serverHome = getProductServerHome();
            SERVERHOMEMAP.put(testId+"_ServerHome", serverHome);//No I18N
            System.setProperty("product.server.home", serverHome);//No I18N
            System.setProperty("server.home", serverHome);
            return serverHome;
        }
        return SERVERHOMEMAP.get(testId+"_ServerHome");
    }

    public static boolean isValidServerDirectory(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        
        File serverDir = new File(path);
        // Check if directory exists and has expected subdirectories
        return serverDir.exists() && serverDir.isDirectory() 
            && new File(serverDir, "conf").exists()//NO I18N
            && new File(serverDir, "bin").exists();//NO I18N
    }


    public static String getAgentHome(){
        return getHomeFromService("agent");//NO I18N
    }
    public static String getDSHome(){
        return getHomeFromService("ds");//NO I18N
    }
    public static String getSGSHome(){
        return getHomeFromService("sgs");//NO I18N
    }
    public static String getVMPHome(){
        return getHomeFromService("vmp");//NO I18N
    }
    public static String getPMPHome(){
        return getHomeFromService("pmp");//NO I18N
    }
    public static String getMSPHome(){
        return getHomeFromService("msp");//NO I18N
    }
    public static String getMDMHome(){
        return getHomeFromService("mdm");//NO I18N
    }
    public static String getSSHome(){
        return getHomeFromService("summary_server");//NO I18N
    }

    /**
     * Extract agent home path from agent service information
     */
    public static String getHomeFromService(String productName) {
        String serviceName = readServiceNameFromConfig(productName);//NO I18N
        if (serviceName == null || serviceName.isEmpty()) {
            LOGGER.severe("Could not determine service name from config for " + productName);//NO I18N
            LOGGER.severe("Please ensure the product_package\\conf\\product-setup.json file is correctly configured.");//NO I18N
            return null; // Return null if service name is not found
        }

        try {
            // First try with 'sc qc' command (works on newer Windows)
            Process process = Runtime.getRuntime().exec("sc qc " + serviceName);//NO I18N
            String binaryPath = extractBinaryPathFromProcess(process);

            if (binaryPath == null) {
                // Try with 'reg query' command as fallback
                process = Runtime.getRuntime().exec(
                        "reg query \"HKEY_LOCAL_MACHINE\\SYSTEM\\CurrentControlSet\\Services\\" + serviceName + "\" /v ImagePath");//NO I18N
                binaryPath = extractBinaryPathFromRegQuery(process);
            }

            if (binaryPath != null) {
                String homePath = extractServerHomeFromBinaryPath(binaryPath);
                LOGGER.info("Extracted binary path: " + homePath);//NO I18N
                System.setProperty(productName+".home", homePath);
                return homePath;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error getting agent service info", e);//NO I18N
        }
        return null;
    }

    /**
     * Read agent service name from the product-service-name.conf file
     */
    private static String readServiceNameFromConfig(String productName) {
        String configFilePath = getToolServerHome() +File.separator+"product_package"+ File.separator + GOATCommonConstants.PRODUCT_SETUP_CONF;
        File configFile = new File(configFilePath);

        if (!configFile.exists()) {
            LOGGER.warning("Config file does not exist: " + configFilePath);//NO I18N
            return null;
        }

        try {
            // Read the JSON file content
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new java.io.FileReader(configFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
            }

            String jsonContent = content.toString();

            // Extract the agent service name using regex pattern matching
            Pattern pattern = Pattern.compile("\""+productName+"\"\\s*:\\s*\\{[^\\}]*\"serviceName\"\\s*:\\s*\"([^\"]*)\"");
            Matcher matcher = pattern.matcher(jsonContent);
            if (matcher.find()) {
                String serviceName = matcher.group(1);
                LOGGER.info("Found agent service name in config: " + serviceName);//NO I18N
                return serviceName;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error reading agent service name from config", e);//NO I18N
        }

        return null;
    }

    /**
     * Extract server home path from service information
     */
    public static String getServerHomeFromService() {
        String serviceName = readProductServiceNameFromConfig();
        if (serviceName == null || serviceName.isEmpty()) {
            LOGGER.warning("Could not determine service name from config, using default");//NO I18N
            serviceName = "uems_service"; // Fallback default
        }

        try {
            // First try with 'sc qc' command (works on newer Windows)
            Process process = Runtime.getRuntime().exec("sc qc " + "\""+serviceName+"\"");//NO I18N
            LOGGER.info("Executing command: sc qc " + "\""+serviceName+"\"");//NO I18N
            String binaryPath = extractBinaryPathFromProcess(process);
            LOGGER.info("Extracted binary path: " + binaryPath);//NO I18N

            if (binaryPath == null) {
                // Try with 'reg query' command as fallback
                process = Runtime.getRuntime().exec(
                        "reg query \"HKEY_LOCAL_MACHINE\\SYSTEM\\CurrentControlSet\\Services\\" + serviceName + "\" /v ImagePath");//NO I18N
                binaryPath = extractBinaryPathFromRegQuery(process);
            }

            if (binaryPath != null) {
                return extractServerHomeFromBinaryPath(binaryPath);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error getting service info", e);//NO I18N
        }

        return null;
    }

    /**
     * Read service name from the test-setup-details.conf file
     */
    private static String readProductServiceNameFromConfig() {
        String configFilePath = getToolServerHome() +TEST_SETUP_DETAILES;
        File configFile = new File(configFilePath);

        if (!configFile.exists()) {
            LOGGER.warning("Config file does not exist: " + configFilePath);//NO I18N
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new java.io.FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Skip empty lines and comments
                if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                    continue;
                }

                // Parse "service_name = value" format
                if (line.contains("service_name")) {
                    // Extract the value with or without quotes
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        String serviceName = parts[1].trim();
                        // Remove quotes if present
                        if (serviceName.startsWith("\"") && serviceName.endsWith("\"")) {
                            serviceName = serviceName.substring(1, serviceName.length() - 1);
                        }
                        LOGGER.info("Found service name in config: " + serviceName);//NO I18N
                        return serviceName;
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error reading service name from config", e);//NO I18N
        }

        return null;
    }
    
    /**
     * Extract binary path from sc qc process output
     */
    private static String extractBinaryPathFromProcess(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("BINARY_PATH_NAME")) {//NO I18N
                    LOGGER.info("Found BINARY_PATH_NAME: " + line);//NO I18N
                    
                    // Extract the binary path portion after the colon and any spaces or quotes
                    int colonPos = line.indexOf(':');
                    if (colonPos != -1) {
                        String path = line.substring(colonPos + 1).trim();
                        // Remove enclosing quotes if present
                        if (path.startsWith("\"")) {
                            int endQuote = path.indexOf("\"", 1);
                            if (endQuote != -1) {
                                path = path.substring(1, endQuote);
                            }
                        }
                        return path;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Extract binary path from registry query output
     */
    private static String extractBinaryPathFromRegQuery(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("ImagePath")) {
                    String[] parts = line.split("REG_SZ");//NO I18N
                    if (parts.length >= 2) {
                        return parts[1].trim();
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Extract server home from binary path
     */
    private static String extractServerHomeFromBinaryPath(String binaryPath) {
        LOGGER.info("Extracting server home from binary path: " + binaryPath);//NO I18N
        
        // Method 1: Extract based on wrapper.exe path
        int binIndex = binaryPath.indexOf(File.separator + "bin" + File.separator + "wrapper.exe");//NO I18N
        if (binIndex != -1) {
            return binaryPath.substring(0, binIndex);
        }
        
        // Method 2: Use regex to find the path before bin\wrapper.exe
        Pattern pattern = Pattern.compile("(.*?)[/\\\\]bin[/\\\\]wrapper\\.exe");//NO I18N
        Matcher matcher = pattern.matcher(binaryPath);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // Method 3: Try to find the first quote-enclosed path that looks like a server dir
        pattern = Pattern.compile("\"([^\"]+)\"");//NO I18N
        matcher = pattern.matcher(binaryPath);
        while (matcher.find()) {
            String path = matcher.group(1);
            if (path.contains("bin") && path.endsWith("wrapper.exe")) {
                return path.substring(0, path.lastIndexOf(File.separator + "bin" + File.separator + "wrapper.exe"));//NO I18N
            }
        }
        
        LOGGER.warning("Could not extract server home from binary path: " + binaryPath);//NO I18N
        return null;
    }
    
    /**
     * Find the latest server installation in ServerDirectory folder
     */
    private static String findLatestServerInstallation() {
        String executionFolder = getCurrentExecutionFolder();
        if (executionFolder == null) {
            return null;
        }
        
        // Check for ServerDirectory in various locations
        String[] potentialParentDirs = {
            executionFolder,
            new File(executionFolder).getParent(),
            "D:\\git\\g.o.a.t\\product_package\\bin"//NO I18N
        };
        
        for (String parentDir : potentialParentDirs) {
            File serverDirFolder = new File(parentDir, "ServerDirectory");//NO I18N
            if (serverDirFolder.exists() && serverDirFolder.isDirectory()) {
                LOGGER.info("Checking for server installations in: " + serverDirFolder.getAbsolutePath());//NO I18N
                
                // List all date-based server directories
                File[] dateDirs = serverDirFolder.listFiles(File::isDirectory);
                if (dateDirs == null || dateDirs.length == 0) {
                    continue;
                }
                
                // Find most recent date directory
                File mostRecentDateDir = null;
                long mostRecentTime = 0;
                
                for (File dateDir : dateDirs) {
                    if (dateDir.lastModified() > mostRecentTime) {
                        mostRecentTime = dateDir.lastModified();
                        mostRecentDateDir = dateDir;
                    }
                }
                
                if (mostRecentDateDir != null) {
                    // Look for UEMS_CentralServer directory inside
                    File[] serverDirs = mostRecentDateDir.listFiles(File::isDirectory);
                    if (serverDirs != null) {
                        for (File dir : serverDirs) {
                            if (dir.getName().equals("UEMS_CentralServer") || //NO I18N
                                dir.getName().contains("Central")) {//NO I18N
                                LOGGER.info("Found server directory: " + dir.getAbsolutePath());//NO I18N
                                return dir.getAbsolutePath();
                            }
                        }
                    }
                    
                    // If no specific server dir found, return the date dir itself
                    return mostRecentDateDir.getAbsolutePath();
                }
            }
        }
        
        return null;
    }

    /**
     * Get server home path from ServerDirectory.txt file
     */
    public static String getServerHomeFromFile() {
        String executionFolder = getCurrentExecutionFolder();
        if (executionFolder == null) {
            return null;
        }
        
        File serverDirFile = new File(executionFolder, "ServerDirectory.txt");//NO I18N
        if (serverDirFile.exists() && serverDirFile.isFile()) {
            try (BufferedReader reader = new BufferedReader(new java.io.FileReader(serverDirFile))) {
                String serverDir = reader.readLine();
                if (serverDir != null && !serverDir.trim().isEmpty()) {
                    return serverDir.trim();
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error reading ServerDirectory.txt", e);//NO I18N
            }
        }
        return null;
    }

    public static Properties getServerConfig(String configFilePath) {
        Properties properties = new Properties();
        String serverHome = getServerHomePath();
        if (serverHome != null) {
            File configFile = new File(serverHome + File.separator + "conf" + File.separator + configFilePath);
            if (configFile.exists() && configFile.isFile()) {
                try (BufferedReader reader = new BufferedReader(new java.io.FileReader(configFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split("=", 2);
                        if (parts.length == 2) {
                            properties.setProperty(parts[0].trim(), parts[1].trim());
                        }
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Error reading config file", e);//NO I18N
                }
            }
        }
        return properties;
    }

    public static String getBuildNumberFromConfig() {
        Properties properties = ServerUtils.getServerConfig("product.conf"); //NO I18N
        LOGGER.info("Build number: " + properties.getProperty("buildnumber"));//NO I18N
        return properties.getProperty("buildnumber");//NO I18N
    }

    public static String getCurrentExecutionFolder() {
        try {
            return new File(".").getCanonicalPath();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error getting current execution folder", e);//NO I18N
        }
        return null;
    }
    
    /**
     * Resolve file path that might contain placeholders like server_home
     */
    public static String resolvePath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "";
        }
        
        // Handle server_home placeholder
        if (filePath.toLowerCase().contains("server_home")) {
            String serverHome = getServerHomePath();
            if (serverHome != null && !serverHome.isEmpty()) {
                return filePath.replaceAll("(?i)server_home", serverHome.replace("\\", "/"));//NO I18N
            } else {
                LOGGER.warning("Could not resolve server_home in path: " + filePath);//NO I18N
                // If server_home can't be resolved, return the original path
                return filePath;
            }
        }
        
        return filePath;
    }

    public static void main(String[] args) {
        // Test server home detection
        String serverHome = getServerHomePath();
        ConsoleOut.println("Detected server home: " + serverHome);//NO I18N
        
        // Test path resolution
        String testPath = "server_home/conf/product.conf";//NO I18N
        String resolvedPath = resolvePath(testPath);
        ConsoleOut.println("Resolved path: " + resolvedPath);//NO I18N
        
        // Test a custom binary path if provided
        if (args.length > 0) {
            String testBinaryPath = args[0];
            ConsoleOut.println("Testing binary path extraction: " + testBinaryPath);//NO I18N
            String extractedHome = extractServerHomeFromBinaryPath(testBinaryPath);
            ConsoleOut.println("Extracted server home: " + extractedHome);//NO I18N
        }
    }
}