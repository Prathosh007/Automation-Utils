package com.me;

import com.me.api.GoatApiApplication;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class GOATMain {

    private static Logger LOGGER;

    public static void main(String[] args) throws IOException {
        String appHome = null;
        try {
            appHome = System.getProperty("goat.app.home");
            if (appHome == null){
                LOGGER.warning("App Home Is Null");
                return;
            }
            initLogger(appHome);

            LOGGER.info("====================================================");
            LOGGER.info("G.O.A.T - Starting API Server with Embedded Explorer");
            LOGGER.info("====================================================");

            long pid = ProcessHandle.current().pid();
            LOGGER.info("PID :: " + pid);

            createPIDFile(appHome, pid);

            int springPort = getSwingPort(appHome);
            LOGGER.info("Spring Port :: " + springPort);
            if (!isPortAvailable(springPort)) {
                LOGGER.severe("Port [" + springPort + "] Is Already In Use ");
                return;
            }

            LOGGER.info("Going To Start Swing Application");
            //Invoke Swing Application
            GoatApiApplication.main(args);
            LOGGER.info("Spring Application Started Successfully");
            LOGGER.info("Going to invoke URL in Browser");
            openUrlInBrowser("http://localhost:%SERVER_PORT%/api/docs".replaceAll("%SERVER_PORT%", String.valueOf(springPort)));

        } catch (Exception e) {
            LOGGER.warning("Exception ::" + e);
            if (appHome != null) {
                deletePIDFile(appHome);
            }
        }
    }

    private static int getSwingPort(String appHome) {
        Properties properties  = new Properties();
        try (InputStream input = GOATMain.class
                .getClassLoader()
                .getResourceAsStream("com/me/api/resources/application.properties")) {

            if (input == null) {
                LOGGER.warning("Sorry, file not found!");
                return 0;
            }

            properties.load(input);
            return Integer.parseInt(properties.getProperty("server.port","0"));
        } catch (IOException e) {
            LOGGER.warning("Failed To Get Spring Server Port :: "+e);
            return 0;
        }
    }

    public static boolean isPortAvailable(int port) {
        try (ServerSocket ss = new ServerSocket(port); DatagramSocket ds = new DatagramSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static void openUrlInBrowser(String url) {
        try {
            // Verify OS is Windows
            String os = System.getProperty("os.name").toLowerCase();
            if (!os.contains("win")) {
                LOGGER.warning("This method is Windows-specific. Current OS: " + os);
                return;
            }

            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", "\"\"", "\"" + url + "\"");
            pb.inheritIO();
            pb.start();

            LOGGER.info("Opened URL in default browser: " + url);
        } catch (IOException e) {
            LOGGER.severe("Failed to open browser: " + e.getMessage());
            LOGGER.warning("Browser Invoke Failed , Kindly Access The URL :: " + url);
        }
    }

    static void initLogger(String appHome) throws IOException {
        LOGGER = Logger.getLogger(GOATMain.class.getName());
        FileHandler fileHandler = new FileHandler(appHome + File.separator + "logs" + File.separator + "/goat_startup_%g.log", true);
        fileHandler.setFormatter(new SimpleFormatter());
        LOGGER.addHandler(fileHandler);
    }

    private static void createPIDFile(String appHome, long pid) {
        try {
            File pidFIle = new File(appHome + File.separator + "bin" + File.separator + "server.pid");
            if (!pidFIle.exists()) {
                pidFIle.createNewFile();
            }

            Files.write(pidFIle.toPath(), String.valueOf(pid).getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING);
            LOGGER.info("Pid File Created Successfully");
        } catch (Exception e) {
            LOGGER.warning("Exception While Creating PID File :: " + e);
        }
    }

    private static void deletePIDFile(String appHome) {
        try {
            File pidFIle = new File(appHome + File.separator + "bin" + File.separator + "server.pid");
            Files.deleteIfExists(pidFIle.toPath());
        } catch (Exception e) {
            LOGGER.warning("Exception While Deleting PID File :: " + e);
        }
    }
}
