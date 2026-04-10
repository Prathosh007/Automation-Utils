//package com.me.util;
//
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.IOException;
//import java.nio.file.Path;
//import java.util.logging.*;
//import java.util.zip.GZIPOutputStream;
//import java.io.FileOutputStream;
//
//import com.me.config.PathConfig;
//
///**
// * Configuration for the logging system
// */
//public class LoggingConfig {
//    private static final String LOG_PATTERN = "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL | %4$-7s | %2$s.%3$s | %5$s%6$s%n"; //No I18N
//    private static final Logger LOGGER = com.me.util.LogManager.getFrameworkLogger(LoggingConfig.class);
//
//    /**
//     * Initialize logging system
//     */
//    public static void initialize() {
//        try {
//            // Create logs directory if it doesn't exist
//            Path logDir = PathConfig.getLogDirectory();
//            if (logDir != null) {
//                new File(logDir.toString()).mkdirs();
//            }
//
//            // Set up framework logger
//            configureLogger("com.me", logDir.resolve("framework/goat.log").toString(), Level.FINE); //No I18N
//
//            // Set up test logger
//            configureLogger("com.me.testcases", logDir.resolve("tests/testcases.log").toString(), Level.INFO); //No I18N
//
//            // Set up API logger for future use
//            configureLogger("com.me.api", logDir.resolve("api/api.log").toString(), Level.INFO); //No I18N
//
//            Logger.getLogger("com.me").info("Logging system initialized"); //No I18N
//
//        } catch (Exception e) {
//            Logger logger = Logger.getLogger(LoggingConfig.class.getName());
//            logger.log(Level.SEVERE, "Error initializing logging system", e); //No I18N
//        }
//    }
//
//    /**
//     * Configure a specific logger with file handler and rotation
//     */
//    private static void configureLogger(String loggerName, String logFilePath, Level level) throws IOException {
//        Logger logger = Logger.getLogger(loggerName);
//        logger.setLevel(level);
//
//        // Create parent directory if it doesn't exist
//        File logFile = new File(logFilePath);
//        if (logFile.getParentFile() != null) {
//            logFile.getParentFile().mkdirs();
//        }
//
//        // Set up file handler with rotation (10MB max file size, 5 files)
//        FileHandler fileHandler = new FileHandler(logFilePath, 10 * 1024 * 1024, 5, true);
//        fileHandler.setFormatter(new SimpleFormatter() {
//            @Override
//            public String format(LogRecord record) {
//                return String.format(LOG_PATTERN,
//                        record.getMillis(),
//                        record.getSourceClassName(),
//                        record.getSourceMethodName(),
//                        record.getLevel().getName(),
//                        record.getMessage(),
//                        record.getThrown() == null ? "" : "\n" + formatStackTrace(record.getThrown())); //No I18N
//            }
//        });
//        fileHandler.setLevel(level);
//
//        // Remove existing handlers of the same type
//        for (Handler handler : logger.getHandlers()) {
//            if (handler instanceof FileHandler) {
//                logger.removeHandler(handler);
//            }
//        }
//
//        // Add the new handler
//        logger.addHandler(fileHandler);
//    }
//
//    /**
//     * Format a throwable stack trace as a string
//     */
//    private static String formatStackTrace(Throwable thrown) {
//        if (thrown == null) {
//            return ""; //No I18N
//        }
//
//        StringBuilder sb = new StringBuilder();
//        sb.append(thrown.toString()).append("\n"); //No I18N
//
//        for (StackTraceElement element : thrown.getStackTrace()) {
//            sb.append("    at ").append(element.toString()).append("\n"); //No I18N
//        }
//
//        return sb.toString();
//    }
//
//    /**
//     * Configure the global LogManager
//     */
//    public static void configureGlobalLogManager() {
//        try {
//            // Apply default logging configuration
//            configureDefaultLogging();
//
//            // Initialize our custom LogManager
//            com.me.util.LogManager.initialize(System.getProperty("goat.log.level"));
//
//        } catch (Exception e) {
//            System.err.println("Error configuring logging system: " + e.getMessage()); //NO I18N
//            e.printStackTrace();
//        }
//    }
//
//    /**
//     * Configure default logging when no properties file is found
//     */
//    private static void configureDefaultLogging() {
//        // Apply default logging configuration
//        System.setProperty("java.util.logging.SimpleFormatter.format", //NO I18N
//                          "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL [%4$s] %2$s: %5$s%6$s%n"); //NO I18N
//
//        // Default log level
//        if (System.getProperty("goat.log.level") == null) { //NO I18N
//            System.setProperty("goat.log.level", "INFO"); //NO I18N
//        }
//    }
//
//    /**
//     * Set the log level for the framework
//     *
//     * @param level The log level to set (INFO, WARNING, SEVERE, etc.)
//     */
//    public static void setLogLevel(String level) {
//        System.setProperty("goat.log.level", level); //NO I18N
//
//        try {
//            // Update existing loggers to the new level
//            Level newLevel = Level.parse(level);
//            java.util.logging.LogManager.getLogManager().getLogger("").setLevel(newLevel);
//        } catch (Exception e) {
//            System.err.println("Error setting log level: " + e.getMessage()); //NO I18N
//        }
//    }
//
//    /**
//     * Enable console logging alongside file logging
//     *
//     * @param enable True to enable console logging, false to disable
//     */
//    public static void enableConsoleLogging(boolean enable) {
//        Logger rootLogger = java.util.logging.LogManager.getLogManager().getLogger("");
//        boolean handlerExists = false;
//
//        for (Handler handler : rootLogger.getHandlers()) {
//            if (handler instanceof ConsoleHandler) {
//                handlerExists = true;
//                if (!enable) {
//                    rootLogger.removeHandler(handler);
//                }
//                break;
//            }
//        }
//
//        if (enable && !handlerExists) {
//            ConsoleHandler consoleHandler = new ConsoleHandler();
//            consoleHandler.setFormatter(new SimpleFormatter() {
//                @Override
//                public String format(LogRecord record) {
//                    return String.format(LOG_PATTERN,
//                            record.getMillis(),
//                            record.getSourceClassName(),
//                            record.getSourceMethodName(),
//                            record.getLevel().getName(),
//                            record.getMessage(),
//                            record.getThrown() == null ? "" : "\n" + formatStackTrace(record.getThrown())); //No I18N
//                }
//            });
//            consoleHandler.setLevel(rootLogger.getLevel());
//            rootLogger.addHandler(consoleHandler);
//        }
//    }
//}
