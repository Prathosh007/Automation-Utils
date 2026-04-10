package com.me.dconpremise.start;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DCStarter {
    private static final Logger LOGGER = Logger.getLogger(DCStarter.class.getName());
    public static String serverhome = System.getProperty("server.home");

    public static void main(String[] args) {
        try {
            System.out.printf("Server home directory: %s%n", new File(serverhome).getCanonicalPath());
            Logger.getLogger("com.me.dconpremise.start").info("Server home directory: " + new File(serverhome).getCanonicalPath());
            // Create ProcessBuilder to execute the batch file
            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", "initialize_goat_internal.bat");

            // Set working directory to the batch file location
            processBuilder.directory(new File(new File( serverhome).getCanonicalPath()+ File.separator + "bin"));

            // Redirect error stream to output stream for easier logging
            processBuilder.redirectErrorStream(true);

            // Start the process
            Process process = processBuilder.start();
            LOGGER.info("Started embedded API server process");

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                LOGGER.info(line);
            }

            // Optional: Wait for process completion
            // Remove if you don't want to block this thread
//            int exitCode = process.waitFor();
//            LOGGER.info("Process exited with code: " + exitCode);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to start embedded API server", e);
        }
    }
}
