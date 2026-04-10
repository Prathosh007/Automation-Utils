package com.me.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for server-related operations
 */
public class ServerUtils {

    private static final Logger LOGGER = LogManager.getLogger(ServerUtils.class, LogManager.LOG_TYPE.GUI);
    private static final Map<String, String> serverHomes = new HashMap<>();


    /**
     * Get the server home directory for a specific app type
     * @param appType The application type (SGS, Backup, PPM, DB)
     * @return The server home directory path
     */
    public static String getServerHome(String appType,String testId) {
        String serverHome = serverHomes.get(appType);
        if (serverHome == null) {
            serverHome = fetchServerHome(appType,testId);
            serverHomes.put(appType,serverHome);
        }
        return serverHome;
    }

    /**
     * Set the server home directory for a specific app type
     * @param appType The application type (SGS, Backup, PPM, DB)
     * @param path The server home directory path
     */
    public static void setServerHome(String appType, String path) {
        serverHomes.put(appType, path);
    }

    private static String fetchServerHome(String appType,String testId){
        switch (appType){
            case "SGS":
            case "SGS_ABOUT":
                String sgsPath = getSGSInstalledPath();
                if (sgsPath == null){
                    return "C:\\Program Files\\ME_Secure_Gateway_Server";//Default Path
                }
                return sgsPath;
            case "BACKUP":
            case "PPM":
            case "DB_MIGRATION":
                return com.me.testcases.ServerUtils.getServerHome(testId);

        }

        return null;
    }


    public static void killProcess(String processName) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "cmd.exe", "/c",
                    "taskkill /F /IM " + processName + " /T"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.info("Process killing output : " + line);
                }
            }
            p.waitFor();
            LOGGER.info("Successfully killed process: " + processName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static String getName(String url){
        URL urlObj = null;
        try {
            urlObj = new URL(url.toString());
        String urlPath = urlObj.getPath();
        String downloadedFileName = urlPath.substring(urlPath.lastIndexOf('/') + 1);

        // Try to extract filename from query parameters if present
        String query = urlObj.getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2 && !pair[1].isEmpty()) {
                    downloadedFileName = pair[1];
                    LOGGER.info("Extracted filename from query parameter key '" + pair[0] + "': " + downloadedFileName);
                    break;
                }
            }
        }
            return downloadedFileName;
        } catch (MalformedURLException e) {
            LOGGER.warning("Exception while extracting filename from URL: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }


    /**
     * Extract server home path from service information
     */
    public static String getSGSInstalledPath() {
        try {
            // First try with 'sc qc' command (works on newer Windows)
            Process process = Runtime.getRuntime().exec("sc qc SecureGatewayServer");//NO I18N
            String binaryPath = extractBinaryPathFromProcess(process);
            LOGGER.info("Binary Path :: "+binaryPath);
            return binaryPath != null ? extractServerHomeFromBinaryPath(binaryPath) : null;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error getting service info", e);//NO I18N
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

}
