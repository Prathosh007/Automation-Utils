package com.me.testcases;

import com.me.Operation;
import com.me.util.LogManager;

import java.io.*;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.me.testcases.ServerUtils.getProductServerHome;
import static com.me.util.CommonUtill.resolveHome;
import static com.me.util.GOATCommonConstants.*;

public class RevertPPM {

    private static final Logger LOGGER = LogManager.getLogger(RevertPPM.class, LogManager.LOG_TYPE.FW);

    public static boolean executeOperation(Operation op) {
        StringBuilder remarks = new StringBuilder();

        try {
            String serverHome = getProductServerHome();
            String version = getLatestModifiedFile(serverHome+PPM_HISTORY_FILE,APPLYING_PATCH_VERSION,serverHome,remarks);
            LOGGER.log(Level.INFO, "*********************REVERT PPM START*********************");
            LOGGER.log(Level.INFO, "revertPPM method is called from ..." .concat(serverHome));
            remarks.append("revertPPM method is called from: ").append(serverHome).append("\n");
            HashMap installMessage = new HashMap<>();
            HashMap revertMessage = new HashMap<>();
            HashMap exceptionMessage = new HashMap<>();
            installMessage.put("install", "Service Pack installed successfully");
            revertMessage.put("revertMsp", "Uninstallation Completed");
            revertMessage.put("revertIntp", "Uninstalled successfully");
            exceptionMessage.put("NullPointer", "might be null in row");
            exceptionMessage.put("Fatal error", "some problem ");
            exceptionMessage.put("NumberFormatException", "number mismatch ");
            exceptionMessage.put("Could not create connection", "connection failed");
            exceptionMessage.put("Error occurred :: Irrevertable exception occurred.", "Irrevertable exception occurred");
            exceptionMessage.put("The ppm file that you have specified is not compatible with this product.", "Compatible issue");
            LOGGER.log(Level.INFO, "install message are " .concat(String.valueOf(installMessage)));
            LOGGER.log(Level.INFO, "revert message are " .concat(String.valueOf(revertMessage)));
            LOGGER.log(Level.INFO, "Exception message are " .concat(String.valueOf(exceptionMessage)));
            String line = null;
            boolean stopFlag = true;
            LOGGER.log(Level.INFO, "product is reverting from..." .concat(serverHome));
            LOGGER.log(Level.INFO, "product version = " .concat(version));
            remarks.append("Reverting product from version: ").append(version).append("\n");
            InputStream is;
            Properties prop = new Properties();
            String productBuildNoFile = serverHome + File.separator + "conf" + File.separator + "product.conf";
            is = new FileInputStream(productBuildNoFile);
            prop.load(is);
            String productBuildNumber = prop.getProperty("buildnumber");

            String realPath = serverHome + File.separator + "bin" + File.separator;
            String driveName;
            driveName = realPath.substring(0, 2);
            String middlePart = realPath.substring(2);
            String revertCommand = PPM_UNINSTALL_COMMENT;

            String ppmPath = serverHome + File.separator + "Patch" + File.separator + version + ".ppm";
            File ppmFile = new File(ppmPath);
            if (!ppmFile.exists()) {
                LOGGER.log(Level.WARNING, "PPM file not found: " + ppmPath);
                remarks.append("PPM file not found: ").append(ppmPath).append("\n");

                // List all .ppm files in the Patch folder
                File patchFolder = new File(serverHome + File.separator + "Patch");
                if (patchFolder.exists() && patchFolder.isDirectory()) {
                    File[] ppmFiles = patchFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".ppm"));
                    if (ppmFiles != null && ppmFiles.length > 0) {
                        remarks.append("Available PPM files in Patch folder:\n");
                        for (File file : ppmFiles) {
                            remarks.append("- ").append(file.getName()).append("\n");
                            LOGGER.log(Level.INFO, "Found PPM file: " + file.getName());
                        }
                    } else {
                        remarks.append("No .ppm files found in Patch folder\n");
                        LOGGER.log(Level.INFO, "No .ppm files found in Patch folder");
                    }
                }
            }
            ppmPath = "\"" + ppmPath + "\"";
            LOGGER.log(Level.INFO, "ppm Path is...".concat(ppmPath));
            String option1 =  "cd \"" +driveName+ middlePart + "\" && " + revertCommand + " " + "-ppmPath" + " " + ppmPath + " " + "-actversion" + " " + version;//+ SPACE + "-version" + SPACE + version + SPACE + "-Dpatch_version" + "=" + version + SPACE + "-Dbuildno_before_ppm" + "=" + productBuildNumber;

            String[] command = new String[3];
            command[0] = "cmd ";
            command[1] = "/c ";
            command[2] = option1;

            LOGGER.log(Level.INFO, "Command executed for revert : " .concat(command[0]).concat(command[1]).concat(command[2]));
            remarks.append("Command executed for revert: ").append(command[0]).append(" ").append(command[1]).append(" ").append(command[2]).append("\n");

            //newly Added code
            File f=new File(serverHome+ File.separator + "Patch" + File.separator + "tmp");
            if(!f.exists())
            {
                f.mkdir();
            }
            Path source= Paths.get(serverHome + File.separator +  "Patch" + File.separator + version +File.separator + "SERVER" +File.separator+"PreInstall"+ File.separator+"desktopcentral_update.jar");
            Path target=Paths.get(serverHome + File.separator + "Patch" + File.separator + "tmp" + File.separator + "desktopcentral_update.jar");
            Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);


            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);


            Process p = processBuilder.start();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));

            while ((line = br.readLine()) != null) {
                LOGGER.log(Level.INFO, "CMD: " .concat(line));
                if (revertMessage.containsValue(line)) {
                    Thread.sleep(10000);
                    Runtime.getRuntime().exec("TASKKILL /F /IM DesktopCentral.exe");
                    LOGGER.log(Level.INFO, "DesktopCentral process is killed :");
                    return true;
                } else if (exceptionMessage.containsKey(line)) {
                    Thread.sleep(10000);
                    return false;
                }
            }
            p.waitFor();
            int exitCode = p.exitValue();
            LOGGER.log(Level.INFO, "exit code for revert ppm " .concat(String.valueOf(exitCode)));
            remarks.append("Exit code for revert operation: ").append(exitCode).append("\n");
            Runtime.getRuntime().exec("TASKKILL /F /IM DesktopCentral.exe");
            LOGGER.log(Level.INFO,"desktopcentral process is killed :");
            op.setRemarks(remarks.toString());
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception in while reverting..." .concat(String.valueOf(e)));
            remarks.append("Exception while reverting: ").append(e.getMessage()).append("\n");
            op.setRemarks(remarks.toString());
        }

        return false;
    }

    /**
     * Gets the most recently modified file from properties file values
     *
     * @param propsFilePath Path to the properties file
     * @param keyName Key to search for in the properties file (can appear multiple times)
     * @param serverHomePath Base path to append to each property value
     * @return Name of the most recently modified file or null if none found
     */
    public static String getLatestModifiedFile(String propsFilePath, String keyName, String serverHomePath,StringBuilder remarks) {
        try {
            LOGGER.log(Level.INFO, "Getting latest file for key '" + keyName + "' from " + propsFilePath);

            propsFilePath = resolvePathVariables(propsFilePath);
            LOGGER.log(Level.INFO, "Resolved properties file path: " + propsFilePath);
            if (!new File(propsFilePath).exists()){
                LOGGER.log(Level.WARNING, "Properties file does not exist: " + propsFilePath);
                remarks.append("Properties file does not exist: ").append(propsFilePath);
                return null;
            }
            // Resolve server home path if it contains variables
            serverHomePath = resolvePathVariables(serverHomePath);
            LOGGER.log(Level.INFO, "Using server home path: " + serverHomePath);

            // Get all values for the specified key
            List<String> paths = getMultipleValuesForKey(propsFilePath, keyName);
            LOGGER.log(Level.INFO, "Found " + paths.size() + " values for key: " + keyName);

            if (paths.isEmpty()) {
                return null;
            }

            // Find the most recently modified file
            File latestFile = null;
            long latestModified = 0;

            for (String relativePath : paths) {
                String fullPath = serverHomePath +File.separator+"Patch"+ File.separator + relativePath;
                File file = new File(fullPath);

                if (file.exists()) {
                    long lastModified = file.lastModified();
                    LOGGER.log(Level.INFO, "File: " + file.getName() + " last modified: " + new Date(lastModified));

                    if (lastModified > latestModified) {
                        latestModified = lastModified;
                        latestFile = file;
                    }
                } else {
                    LOGGER.log(Level.INFO, "File does not exist: " + fullPath);
                }
            }

            if (latestFile != null) {
                LOGGER.log(Level.INFO, "Latest modified file: " + latestFile.getName());
                return latestFile.getName();
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error finding latest modified file: " + e.getMessage(), e);
        }

        return null;
    }

    /**
     * Reads multiple values for the same key from a properties file
     */
    private static List<String> getMultipleValuesForKey(String filePath, String keyToFind) throws IOException {
        List<String> values = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                    continue;
                }

                // Handle line continuations
                while (line.endsWith("\\")) {
                    line = line.substring(0, line.length() - 1);
                    String nextLine = reader.readLine();
                    if (nextLine == null) break;
                    line += nextLine.trim();
                }

                // Parse key-value pair
                int separatorIndex = findFirstSeparator(line);
                if (separatorIndex > 0) {
                    String key = line.substring(0, separatorIndex).trim();
                    String value = line.substring(separatorIndex + 1).trim();

                    if (key.equals(keyToFind)) {
                        values.add(value);
                    }
                }
            }
        }

        return values;
    }

    /**
     * Finds the first non-escaped separator (= or :) in a properties line
     */
    private static int findFirstSeparator(String line) {
        boolean escaped = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '=' || c == ':') {
                return i;
            }
        }

        return -1;
    }

    /**
     * Resolves path variables like server_home in the given path
     */
    private static String resolvePathVariables(String path) {
        if (path == null) {
            return null;
        }

        // Use existing resolveHome method if available
        return resolveHome(path);
    }



}
