package com.me.testcases;

import com.me.Operation;
import com.me.util.DownloadFile;
import com.me.util.LogManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.me.testcases.ExeInstall.DEFAULT_DOWNLOAD_DIR;
import static com.me.testcases.ServerUtils.getProductServerHome;
import static com.me.util.GOATCommonConstants.PPM_DOWNLOAD_FILE_NAME;
import static com.me.util.GOATCommonConstants.PPM_INSTALL_COMMENT;
import static com.me.util.ProcessScanner.runCMDCommand;

public class InstallPPM {

    private static final Logger LOGGER = LogManager.getLogger(InstallPPM.class, LogManager.LOG_TYPE.FW);

    public static boolean executeOperation(Operation op){
        LOGGER.log(Level.INFO, "Entering executeOperation method with operation: " + op);
        try {
            String ppmPath = op.getParameter("url");
//            PPM_DOWNLOAD_FILE_NAME = ppmPath.substring(ppmPath.lastIndexOf("/") + 1);

            try {
                URL urlObj = new URL(ppmPath);
                String urlPath = urlObj.getPath();
                PPM_DOWNLOAD_FILE_NAME = urlPath.substring(urlPath.lastIndexOf('/') + 1);

                // Try to extract filename from query parameters if present
                String query = urlObj.getQuery();
                if (query != null) {
                    for (String param : query.split("&")) {
                        String[] pair = param.split("=", 2);
                        if (pair.length == 2 && !pair[1].isEmpty()) {
                            PPM_DOWNLOAD_FILE_NAME = pair[1];
                            LOGGER.info("Extracted filename from query parameter key '" + pair[0] + "': " + PPM_DOWNLOAD_FILE_NAME);
                            break;
                        }
                    }
                }

            } catch (Exception e) {
                op.setRemarks("Failed to extract filename from URL kindly check the URL");
                LOGGER.info("Failed to extract filename from URL ; " + e);
                return false;
            }


            DownloadFile.downloadFile(ppmPath, DEFAULT_DOWNLOAD_DIR, "sambathkumar.mm:4f96f228973e4c3ab98fa671fcb88712",op);

            String ppmDownloadPath = new File(DEFAULT_DOWNLOAD_DIR + File.separator + PPM_DOWNLOAD_FILE_NAME).getCanonicalPath();
            LOGGER.log(Level.INFO,"PPM file downloaded successfully to: " + ppmDownloadPath);
            String homePath = System.getProperty("server.home");
            if (homePath == null || homePath.isEmpty()) {
                LOGGER.log(Level.SEVERE, "Product server home is not set");
                homePath = getProductServerHome();
            }
            ppmDownloadPath = "\"" + ppmDownloadPath + "\"";
            LOGGER.log(Level.INFO, "*********************INSTALL PPM START*********************");
            LOGGER.log(Level.INFO, "ppm installation process start from  ".concat(homePath));
            LOGGER.log(Level.INFO, "ppm path, goint to apply ".concat(ppmDownloadPath));
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

            boolean stopFlag = true;
            String line = null;
            String realPath = homePath + File.separator + "bin" + File.separator;
            if (stopFlag == true) {
                String driveName;
                driveName = realPath.substring(0, 2);
                String middlePart = realPath.substring(2);
                middlePart = middlePart.replace("/", File.separator);
                String installCommand = PPM_INSTALL_COMMENT;

                String option1 = "cd " +driveName+ middlePart + " && " + installCommand + " " + "-ppmPath" + " " + ppmDownloadPath;

                LOGGER.log(Level.INFO, "Command executed for installation : "+option1);

                // Extract directory and command parts
                String command = option1;
                ProcessBuilder processBuilder = new ProcessBuilder();

                if (command.contains("cd ") && command.contains(" && ")) {
                    // Parse the directory path and the actual command
                    String dirPath = command.substring(command.indexOf("cd ") + 3, command.indexOf(" && "));
                    String actualCommand = command.substring(command.indexOf(" && ") + 4);

                    // Set working directory for ProcessBuilder
                    File dir = new File(dirPath.trim().replace("\"", ""));
                    processBuilder.directory(dir);

                    // Set the command to execute in that directory
                    processBuilder.command("cmd.exe", "/s", "/c", actualCommand);

                    LOGGER.log(Level.INFO, "Setting directory to: " + dir.getAbsolutePath());
                    LOGGER.log(Level.INFO, "Executing command: " + actualCommand);
                } else {
                    // Use the original command approach
                    processBuilder.command("cmd.exe", "/s", "/c", command);
                    LOGGER.log(Level.INFO, "Executing command: " + command);
                }

                processBuilder.redirectErrorStream(true);
                Process process = processBuilder.start();

                BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));

                while ((line = br.readLine()) != null) {
                    LOGGER.log(Level.INFO, "CMD: ".concat(line));

                    if (installMessage.containsValue(line)) {
                        Thread.sleep(10000);
                        LOGGER.log(Level.INFO, "Exiting executeOperation method with result: " + line);
                        op.setRemarks("Exiting executeOperation method with result: " + line);
                        return true;
                    } else if (revertMessage.containsValue(line)) {
                        Thread.sleep(10000);
                        LOGGER.log(Level.INFO, "Exiting executeOperation method with result: " + line);
                        op.setRemarks("Exiting executeOperation method with result: " + line);
                        return false;
                    } else if (exceptionMessage.containsKey(line)) {
                        Thread.sleep(10000);
                        LOGGER.log(Level.INFO, "Exiting executeOperation method with result: " + line);
                        op.setRemarks("Exiting executeOperation method with result: " + line);
                        return false;
                    }
                }

                LOGGER.info("Waiting for PPM process to complete...");
                int exitCode = process.waitFor();
                LOGGER.log(Level.INFO, "Process exit code: " + exitCode);
                if (exitCode == 0) {
                    LOGGER.log(Level.INFO, "Exiting executeOperation method with successful exit code");
                    op.setRemarks("Exiting executeOperation method with successful exit code");
                    return true;
                } else {
                    LOGGER.log(Level.SEVERE, "Exiting executeOperation method with failed exit code: " + exitCode);
                    op.setRemarks("Exiting executeOperation method with failed exit code: " + exitCode);
                    return false;
                }
//                while ((line = br.readLine()) != null) {
//                    LOGGER.log(Level.INFO, "CMD: ".concat(line));
//
//                    if (installMessage.containsValue(line)) {
//                        Thread.sleep(10000);
//                        LOGGER.log(Level.INFO, "Exiting executeOperation method with result: " + line);
//                        return true;
//                    } else if (revertMessage.containsValue(line)) {
//                        Thread.sleep(10000);
//                        LOGGER.log(Level.INFO, "Exiting executeOperation method with result: " + line);
//                        return false;
//                    } else if (exceptionMessage.containsKey(line)) {
//                        Thread.sleep(10000);
//                        LOGGER.log(Level.INFO, "Exiting executeOperation method with result: " + line);
//                        return false;
//                    }
//                }
//                LOGGER.log(Level.INFO, "Exiting executeOperation method with result: " + line);
//                return true;
            } else {
                LOGGER.log(Level.INFO, "Product is not stopped");
                LOGGER.log(Level.INFO, "Exiting executeOperation method with result: null");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception while installing ppm " .concat(String.valueOf(e)));
            LOGGER.log(Level.INFO, "Exiting executeOperation method with result: null due to exception");
        }

        LOGGER.log(Level.INFO, "Exiting executeOperation method with result: null");
        return true;
    }
}
