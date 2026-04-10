//package com.me.testcases;
//
//import com.me.LLAMAClient;
//import com.me.Operation;
//import com.me.TestCase;
//import com.me.TestExecutor;
//import com.me.TestCaseReader;
//
//import java.util.logging.Logger;
//import java.util.logging.Level;
//import java.util.logging.ConsoleHandler;
//import java.util.logging.SimpleFormatter;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.HashMap;
//import java.io.File;
//import java.io.IOException;
//import java.io.FileReader;
//import java.io.BufferedReader;
//import java.io.InputStreamReader;
//
//import com.google.gson.JsonParser;
//import com.adventnet.mfw.ConsoleOut;
//
///**
// * Comprehensive tester class for G.O.A.T (Generic Orchestrated Automated Testing)
// * This class allows testing of individual components or the complete workflow
// */
//public class GOATTester {
//    private static final Logger LOGGER = Logger.getLogger(GOATTester.class.getName());
//
//    // Default file paths
//    private static final String DEFAULT_EXCEL_PATH = "manual_cases/testcases.xlsx"; //NO I18N
//    private static final String DEFAULT_JSON_PATH = "manual_cases/testcases.json"; //NO I18N
//    private static final String DEFAULT_RAW_OUTPUT_PATH = "manual_cases/raw_response.json"; //NO I18N
//    private static final String DEFAULT_EXTRACTED_OUTPUT_PATH = "manual_cases/extracted_response.json"; //NO I18N
//    private static final String DEFAULT_RESULTS_PATH = "logs/test_results.json"; //NO I18N
//    private static final String DEFAULT_FAILED_TESTS_PATH = "manual_cases/failed_testcases.json"; //NO I18N
//
//    static {
//        // Configure logging to show more detailed info
//        LOGGER.setLevel(Level.INFO);
//        ConsoleHandler handler = new ConsoleHandler();
//        handler.setFormatter(new SimpleFormatter());
//        handler.setLevel(Level.INFO);
//        LOGGER.addHandler(handler);
//    }
//
//    public static void main(String[] args) {
//        if (args.length > 0) {
//            // Command-line mode
//            processCommandLineArgs(args);
//            return;
//        }
//
//        // Interactive mode with simplified options
//        try {
//            showMainMenu();
//        } catch (Exception e) {
//            LOGGER.log(Level.SEVERE, "Error in main menu", e); //NO I18N
//        }
//    }
//
//    /**
//     * Show the main menu with simplified options
//     */
//    private static void showMainMenu() throws Exception {
//        while (true) {
//            ConsoleOut.println("\n=================================================================="); //NO I18N
//            ConsoleOut.println("                G.O.A.T - MAIN MENU                               "); //NO I18N
//            ConsoleOut.println("=================================================================="); //NO I18N
//            ConsoleOut.println("1. JSON Generation (Convert Test Cases & Generate Extracted JSON)"); //NO I18N
//            ConsoleOut.println("2. Testing Only (Execute Tests from Extracted JSON)"); //NO I18N
//            ConsoleOut.println("3. Full Workflow (JSON Generation + Testing in one go)"); //NO I18N
//            ConsoleOut.println("4. Re-Run Failed Tests (from previous run)"); //NO I18N
//            ConsoleOut.println("5. Advanced Options (Individual Operations)"); //NO I18N
//            ConsoleOut.println("0. Exit"); //NO I18N
//            ConsoleOut.println("=================================================================="); //NO I18N
//
//            int choice = getUserChoice("Select an option (0-5): "); //NO I18N
//
//            switch (choice) {
//                case 0:
//                    ConsoleOut.println("Exiting G.O.A.T Tester. Goodbye!"); //NO I18N
//                    return;
//                case 1:
//                    runJsonGeneration();
//                    break;
//                case 2:
//                    runTestingOnly();
//                    break;
//                case 3:
//                    runFullWorkflow();
//                    break;
//                case 4:
//                    rerunFailedTests();
//                    break;
//                case 5:
//                    showAdvancedMenu();
//                    break;
//                default:
//                    ConsoleOut.println("Invalid choice. Please try again."); //NO I18N
//            }
//        }
//    }
//
//    /**
//     * Re-run failed tests from a previous test run
//     */
//    private static void rerunFailedTests() throws Exception {
//        ConsoleOut.println("\n==== RE-RUN FAILED TEST CASES ===="); //NO I18N
//
//        String resultsPath = getUserInput("Enter path to test results [" + DEFAULT_RESULTS_PATH + "]: "); //NO I18N
//        if (resultsPath.trim().isEmpty()) {
//            resultsPath = DEFAULT_RESULTS_PATH;
//        }
//
//        String extractedJson = getUserInput("Enter path to original extracted JSON [" + DEFAULT_EXTRACTED_OUTPUT_PATH + "]: "); //NO I18N
//        if (extractedJson.trim().isEmpty()) {
//            extractedJson = DEFAULT_EXTRACTED_OUTPUT_PATH;
//        }
//
//        String failedTestsJson = getUserInput("Enter path for failed tests JSON [" + DEFAULT_FAILED_TESTS_PATH + "]: "); //NO I18N
//        if (failedTestsJson.trim().isEmpty()) {
//            failedTestsJson = DEFAULT_FAILED_TESTS_PATH;
//        }
//
//        resultsPath = ensureAbsolutePath(resultsPath);
//        extractedJson = ensureAbsolutePath(extractedJson);
//        failedTestsJson = ensureAbsolutePath(failedTestsJson);
//
//        File resultsFile = new File(resultsPath);
//        File extractedFile = new File(extractedJson);
//
//        if (!resultsFile.exists()) {
//            ConsoleOut.println("\nError: Test results file not found: " + resultsPath); //NO I18N
//            waitForEnter();
//            return;
//        }
//
//        if (!extractedFile.exists()) {
//            ConsoleOut.println("\nError: Extracted JSON file not found: " + extractedJson); //NO I18N
//            waitForEnter();
//            return;
//        }
//
//        // Check if the results file contains failures
//        ConsoleOut.println("\nAnalyzing test results for failures..."); //NO I18N
//        ResultAnalyzer analyzer = new ResultAnalyzer();
//
//        try {
//            // Check for failures
//            boolean hasFailures = analyzer.hasFailures(resultsPath);
//
//            if (!hasFailures) {
//                ConsoleOut.println("\nNo failed test cases found in the previous run. All tests passed!"); //NO I18N
//                waitForEnter();
//                return;
//            }
//
//            ConsoleOut.println("\nFound failed test cases. Extracting to separate JSON file..."); //NO I18N
//
//            // Extract failed test cases to a new JSON file
//            ensureParentDirectoriesExist(failedTestsJson);
//            analyzer.extractFailedTestCases(resultsPath, extractedJson, failedTestsJson);
//
//            ConsoleOut.println("\nFailed test cases extracted to: " + failedTestsJson); //NO I18N
//            ConsoleOut.println("\nRe-running failed test cases..."); //NO I18N
//
//            // Run tests with the extracted failed test cases
//            testCompleteWorkflow(failedTestsJson, resultsPath);
//
//            ConsoleOut.println("\nFailed test cases re-run complete."); //NO I18N
//            ConsoleOut.println("Updated test results are available in: " + resultsPath); //NO I18N
//
//        } catch (Exception e) {
//            ConsoleOut.println("\nError analyzing or running failed tests: " + e.getMessage()); //NO I18N
//            LOGGER.log(Level.SEVERE, "Error in rerunFailedTests", e); //NO I18N
//        }
//
//        waitForEnter();
//    }
//
//    /**
//     * Show advanced menu with more detailed options
//     */
//    private static void showAdvancedMenu() throws Exception {
//        while (true) {
//            ConsoleOut.println("\n=================================================================="); //NO I18N
//            ConsoleOut.println("                G.O.A.T - ADVANCED OPTIONS                        "); //NO I18N
//            ConsoleOut.println("=================================================================="); //NO I18N
//            ConsoleOut.println("1. Convert Test Cases"); //NO I18N
//            ConsoleOut.println("2. Run LLAMA Processing"); //NO I18N
//            ConsoleOut.println("3. Test Individual Operation"); //NO I18N
//            ConsoleOut.println("4. Test EXE Installation"); //NO I18N
//            ConsoleOut.println("5. View Test Results"); //NO I18N
//            ConsoleOut.println("0. Back to Main Menu"); //NO I18N
//            ConsoleOut.println("=================================================================="); //NO I18N
//
//            int choice = getUserChoice("Select an option (0-5): "); //NO I18N
//
//            switch (choice) {
//                case 0:
//                    return;
//                case 1:
//                    runTestCaseConversion();
//                    break;
//                case 2:
//                    runLlamaProcessing();
//                    break;
//                case 3:
//                    runIndividualOperation();
//                    break;
//                case 4:
//                    runExeInstallTest();
//                    break;
//                case 5:
//                    viewTestResults();
//                    break;
//                default:
//                    ConsoleOut.println("Invalid choice. Please try again."); //NO I18N
//            }
//        }
//    }
//
//    /**
//     * View test results summary
//     */
//    private static void viewTestResults() throws Exception {
//        ConsoleOut.println("\n==== TEST RESULTS SUMMARY ===="); //NO I18N
//
//        String resultsPath = getUserInput("Enter path to test results [" + DEFAULT_RESULTS_PATH + "]: "); //NO I18N
//        if (resultsPath.trim().isEmpty()) {
//            resultsPath = DEFAULT_RESULTS_PATH;
//        }
//
//        resultsPath = ensureAbsolutePath(resultsPath);
//        File resultsFile = new File(resultsPath);
//
//        if (!resultsFile.exists()) {
//            ConsoleOut.println("\nError: Test results file not found: " + resultsPath); //NO I18N
//            waitForEnter();
//            return;
//        }
//
//        ConsoleOut.println("\nDisplaying test results summary from: " + resultsPath); //NO I18N
//        ConsoleOut.println("\n----------------------------------------"); //NO I18N
//
//        ResultAnalyzer analyzer = new ResultAnalyzer();
//        analyzer.printResultsSummary(resultsPath);
//
//        waitForEnter();
//    }
//
//    /**
//     * Run the JSON generation process (conversion + LLAMA)
//     */
//    private static void runJsonGeneration() throws Exception {
//        ConsoleOut.println("\n==== JSON GENERATION WORKFLOW ===="); //NO I18N
//
//        // Get input/output paths
//        String excelPath = getUserInput("Enter Excel/CSV file path [" + DEFAULT_EXCEL_PATH + "]: "); //NO I18N
//        if (excelPath.trim().isEmpty()) {
//            excelPath = DEFAULT_EXCEL_PATH;
//        }
//
//        String jsonPath = getUserInput("Enter intermediate JSON output path [" + DEFAULT_JSON_PATH + "]: "); //NO I18N
//        if (jsonPath.trim().isEmpty()) {
//            jsonPath = DEFAULT_JSON_PATH;
//        }
//
//        String rawOutput = getUserInput("Enter raw LLAMA output path [" + DEFAULT_RAW_OUTPUT_PATH + "]: "); //NO I18N
//        if (rawOutput.trim().isEmpty()) {
//            rawOutput = DEFAULT_RAW_OUTPUT_PATH;
//        }
//
//        String extractedOutput = getUserInput("Enter extracted JSON output path [" + DEFAULT_EXTRACTED_OUTPUT_PATH + "]: "); //NO I18N
//        if (extractedOutput.trim().isEmpty()) {
//            extractedOutput = DEFAULT_EXTRACTED_OUTPUT_PATH;
//        }
//
//        // Make paths absolute
//        excelPath = ensureAbsolutePath(excelPath);
//        jsonPath = ensureAbsolutePath(jsonPath);
//        rawOutput = ensureAbsolutePath(rawOutput);
//        extractedOutput = ensureAbsolutePath(extractedOutput);
//
//        // Create directories if they don't exist
//        ensureParentDirectoriesExist(jsonPath);
//        ensureParentDirectoriesExist(rawOutput);
//        ensureParentDirectoriesExist(extractedOutput);
//
//        // Step 1: Convert test cases
//        ConsoleOut.println("\n[STEP 1/2] Converting test cases from Excel/CSV to JSON..."); //NO I18N
//        convertTestCases(excelPath, jsonPath);
//
//        // Step 2: Run LLAMA processing
//        ConsoleOut.println("\n[STEP 2/2] Processing JSON with LLAMA API..."); //NO I18N
//        testLLAMAResponse(jsonPath, rawOutput, extractedOutput);
//
//        ConsoleOut.println("\n==== JSON GENERATION COMPLETED SUCCESSFULLY ===="); //NO I18N
//        ConsoleOut.println("Extracted JSON saved to: " + extractedOutput); //NO I18N
//        ConsoleOut.println("You can now use this JSON for testing (Option 2 in main menu)."); //NO I18N
//        waitForEnter();
//    }
//
//    /**
//     * Run the testing process from extracted JSON
//     */
//    private static void runTestingOnly() throws Exception {
//        ConsoleOut.println("\n==== TESTING FROM EXTRACTED JSON ===="); //NO I18N
//
//        String extractedJson = getUserInput("Enter path to extracted JSON [" + DEFAULT_EXTRACTED_OUTPUT_PATH + "]: "); //NO I18N
//        if (extractedJson.trim().isEmpty()) {
//            extractedJson = DEFAULT_EXTRACTED_OUTPUT_PATH;
//        }
//
//        String resultsPath = getUserInput("Enter path for test results [" + DEFAULT_RESULTS_PATH + "]: "); //NO I18N
//        if (resultsPath.trim().isEmpty()) {
//            resultsPath = DEFAULT_RESULTS_PATH;
//        }
//
//        extractedJson = ensureAbsolutePath(extractedJson);
//        resultsPath = ensureAbsolutePath(resultsPath);
//
//        File jsonFile = new File(extractedJson);
//        if (!jsonFile.exists()) {
//            ConsoleOut.println("\nError: File not found: " + extractedJson); //NO I18N
//            ConsoleOut.println("Please generate the JSON first (Option 1 in main menu) or specify a valid file path."); //NO I18N
//
//            // Ask if user wants to specify another file
//            String retry = getUserInput("\nWould you like to specify another file path? (y/n): "); //NO I18N
//            if (retry.trim().toLowerCase().startsWith("y")) {
//                runTestingOnly();
//                return;
//            }
//
//            waitForEnter();
//            return;
//        }
//
//        // Verify it's a valid JSON file
//        try {
//            try (FileReader reader = new FileReader(jsonFile)) {
//                JsonParser.parseReader(reader);
//            }
//        } catch (Exception e) {
//            ConsoleOut.println("\nError: Invalid JSON file: " + e.getMessage()); //NO I18N
//            ConsoleOut.println("Please ensure the file contains valid JSON."); //NO I18N
//            waitForEnter();
//            return;
//        }
//
//        ConsoleOut.println("\nExecuting tests based on extracted JSON from: " + extractedJson); //NO I18N
//        ConsoleOut.println("This will run all test cases defined in the JSON file."); //NO I18N
//        ConsoleOut.println("Test results will be saved to: " + resultsPath); //NO I18N
//        ConsoleOut.println("\nExecuting..."); //NO I18N
//
//        // Ensure the results path directory exists
//        ensureParentDirectoriesExist(resultsPath);
//
//        testCompleteWorkflow(extractedJson, resultsPath);
//
//        ConsoleOut.println("\n==== TESTING COMPLETED ===="); //NO I18N
//        ConsoleOut.println("Test results saved to: " + resultsPath); //NO I18N
//
//        // Ask if user wants to view results summary
//        String viewResults = getUserInput("\nWould you like to view the test results summary? (y/n): "); //NO I18N
//        if (viewResults.trim().toLowerCase().startsWith("y")) {
//            ResultAnalyzer analyzer = new ResultAnalyzer();
//            analyzer.printResultsSummary(resultsPath);
//        }
//
//        waitForEnter();
//    }
//
//    /**
//     * Run the complete workflow without interruption
//     */
//    private static void runFullWorkflow() throws Exception {
//        ConsoleOut.println("\n==== FULL WORKFLOW EXECUTION ===="); //NO I18N
//        ConsoleOut.println("This will run the complete workflow:"); //NO I18N
//        ConsoleOut.println("1. Convert test cases from Excel/CSV to JSON"); //NO I18N
//        ConsoleOut.println("2. Process JSON with LLAMA API"); //NO I18N
//        ConsoleOut.println("3. Execute tests based on extracted JSON"); //NO I18N
//
//        // Get input/output paths
//        String excelPath = getUserInput("\nEnter Excel/CSV file path [" + DEFAULT_EXCEL_PATH + "]: "); //NO I18N
//        if (excelPath.trim().isEmpty()) {
//            excelPath = DEFAULT_EXCEL_PATH;
//        }
//
//        String resultsPath = getUserInput("Enter path for test results [" + DEFAULT_RESULTS_PATH + "]: "); //NO I18N
//        if (resultsPath.trim().isEmpty()) {
//            resultsPath = DEFAULT_RESULTS_PATH;
//        }
//
//        // Make paths absolute and ensure directories exist
//        excelPath = ensureAbsolutePath(excelPath);
//        String jsonPath = ensureAbsolutePath(DEFAULT_JSON_PATH);
//        String rawOutput = ensureAbsolutePath(DEFAULT_RAW_OUTPUT_PATH);
//        String extractedOutput = ensureAbsolutePath(DEFAULT_EXTRACTED_OUTPUT_PATH);
//        resultsPath = ensureAbsolutePath(resultsPath);
//
//        ensureParentDirectoriesExist(jsonPath);
//        ensureParentDirectoriesExist(rawOutput);
//        ensureParentDirectoriesExist(extractedOutput);
//        ensureParentDirectoriesExist(resultsPath);
//
//        // Step 1: Convert test cases
//        ConsoleOut.println("\n[STEP 1/3] Converting test cases from Excel/CSV to JSON..."); //NO I18N
//        convertTestCases(excelPath, jsonPath);
//
//        // Step 2: Run LLAMA processing
//        ConsoleOut.println("\n[STEP 2/3] Processing JSON with LLAMA API..."); //NO I18N
//        testLLAMAResponse(jsonPath, rawOutput, extractedOutput);
//
//        // Step 3: Execute tests
//        ConsoleOut.println("\n[STEP 3/3] Executing tests based on extracted JSON..."); //NO I18N
//        testCompleteWorkflow(extractedOutput, resultsPath);
//
//        ConsoleOut.println("\n==== FULL WORKFLOW COMPLETED SUCCESSFULLY ===="); //NO I18N
//        ConsoleOut.println("Test results saved to: " + resultsPath); //NO I18N
//
//        // Ask if user wants to view results summary
//        String viewResults = getUserInput("\nWould you like to view the test results summary? (y/n): "); //NO I18N
//        if (viewResults.trim().toLowerCase().startsWith("y")) {
//            ResultAnalyzer analyzer = new ResultAnalyzer();
//            analyzer.printResultsSummary(resultsPath);
//        }
//
//        waitForEnter();
//    }
//
//    /**
//     * Helper method for test case conversion
//     */
//    private static void runTestCaseConversion() throws Exception {
//        ConsoleOut.println("\n==== TEST CASE CONVERSION ===="); //NO I18N
//
//        String inputFile = getUserInput("Enter input file path (Excel/CSV): "); //NO I18N
//        String outputFile = getUserInput("Enter output JSON path: "); //NO I18N
//
//        if (inputFile.isEmpty() || outputFile.isEmpty()) {
//            ConsoleOut.println("Error: Input and output paths cannot be empty."); //NO I18N
//            waitForEnter();
//            return;
//        }
//
//        inputFile = ensureAbsolutePath(inputFile);
//        outputFile = ensureAbsolutePath(outputFile);
//        ensureParentDirectoriesExist(outputFile);
//
//        ConsoleOut.println("Converting test cases..."); //NO I18N
//        convertTestCases(inputFile, outputFile);
//
//        ConsoleOut.println("Conversion completed successfully."); //NO I18N
//        waitForEnter();
//    }
//
//    /**
//     * Helper method for LLAMA processing
//     */
//    private static void runLlamaProcessing() throws Exception {
//        ConsoleOut.println("\n==== LLAMA PROCESSING ===="); //NO I18N
//
//        String jsonFile = getUserInput("Enter input JSON path: "); //NO I18N
//        String rawOutput = getUserInput("Enter raw output path: "); //NO I18N
//        String extractedOutput = getUserInput("Enter extracted output path: "); //NO I18N
//
//        if (jsonFile.isEmpty() || rawOutput.isEmpty() || extractedOutput.isEmpty()) {
//            ConsoleOut.println("Error: All file paths must be specified."); //NO I18N
//            waitForEnter();
//            return;
//        }
//
//        jsonFile = ensureAbsolutePath(jsonFile);
//        rawOutput = ensureAbsolutePath(rawOutput);
//        extractedOutput = ensureAbsolutePath(extractedOutput);
//
//        ensureParentDirectoriesExist(rawOutput);
//        ensureParentDirectoriesExist(extractedOutput);
//
//        ConsoleOut.println("Processing with LLAMA API..."); //NO I18N
//        testLLAMAResponse(jsonFile, rawOutput, extractedOutput);
//
//        ConsoleOut.println("LLAMA processing completed successfully."); //NO I18N
//        waitForEnter();
//    }
//
//    /**
//     * Helper method for individual operation testing
//     */
//    private static void runIndividualOperation() throws Exception {
//        ConsoleOut.println("\n==== TEST INDIVIDUAL OPERATION ===="); //NO I18N
//        ConsoleOut.println("Available operations:"); //NO I18N
//        ConsoleOut.println("1. check_presence"); //NO I18N
//        ConsoleOut.println("2. verify_absence"); //NO I18N
//        ConsoleOut.println("3. value_should_be_present"); //NO I18N
//        ConsoleOut.println("4. value_should_be_removed"); //NO I18N
//
//        int opChoice = getUserChoice("Select operation type (1-4): "); //NO I18N
//        String operationType;
//
//        switch (opChoice) {
//            case 1: operationType = "check_presence"; break; //NO I18N
//            case 2: operationType = "verify_absence"; break; //NO I18N
//            case 3: operationType = "value_should_be_present"; break; //NO I18N
//            case 4: operationType = "value_should_be_removed"; break; //NO I18N
//            default:
//                ConsoleOut.println("Invalid choice."); //NO I18N
//                waitForEnter();
//                return;
//        }
//
//        String filePath = getUserInput("Enter file path: "); //NO I18N
//        String fileName = getUserInput("Enter file name: "); //NO I18N
//        String value = getUserInput("Enter value (leave empty if not applicable): "); //NO I18N
//        String productName = getUserInput("Enter product name (leave empty if not applicable): "); //NO I18N
//
//        if (filePath.isEmpty() || fileName.isEmpty()) {
//            ConsoleOut.println("Error: File path and name cannot be empty."); //NO I18N
//            waitForEnter();
//            return;
//        }
//
//        ConsoleOut.println("Executing operation..."); //NO I18N
//        testOperations(operationType, filePath, fileName, value, productName);
//
//        waitForEnter();
//    }
//
//    /**
//     * Helper method for EXE installation testing
//     */
//    private static void runExeInstallTest() throws Exception {
//        ConsoleOut.println("\n==== TEST EXE INSTALLATION ===="); //NO I18N
//
//        String productName = getUserInput("Enter product name [ManageEngine_Endpoint_Central_Setup]: "); //NO I18N
//        if (productName.trim().isEmpty()) {
//            productName = "ManageEngine_Endpoint_Central_Setup"; //NO I18N
//        }
//
//        ConsoleOut.println("Select installation mode:"); //NO I18N
//        ConsoleOut.println("1. Test mode (simulated)"); //NO I18N
//        ConsoleOut.println("2. Production mode (actual)"); //NO I18N
//
//        int modeChoice = getUserChoice("Select mode (1-2): "); //NO I18N
//        boolean testMode = (modeChoice == 1);
//
//        ConsoleOut.println("Executing EXE installation test..."); //NO I18N
//        testExeInstall(productName, testMode);
//
//        waitForEnter();
//    }
//
//    /**
//     * Process command line arguments for backward compatibility
//     */
//    private static void processCommandLineArgs(String[] args) {
//        try {
//            String command = args[0].toLowerCase();
//
//            switch (command) {
//                case "help": //NO I18N
//                    printUsage();
//                    break;
//                case "convert-testcases": //NO I18N
//                    if (args.length < 3) {
//                        LOGGER.warning("Insufficient arguments for test case conversion"); //NO I18N
//                        printUsage();
//                        return;
//                    }
//                    convertTestCases(args[1], args[2]);
//                    break;
//                case "test-llama": //NO I18N
//                    testLLAMAResponse(args);
//                    break;
//                case "test-operations": //NO I18N
//                    if (args.length < 6) {
//                        LOGGER.warning("Insufficient arguments for testing operations"); //NO I18N
//                        printUsage();
//                        return;
//                    }
//                    testOperations(args[1], args[2], args[3], args[4], args[5]);
//                    break;
//                case "test-workflow": //NO I18N
//                    String jsonFilePath = args.length > 1 ? args[1] : DEFAULT_EXTRACTED_OUTPUT_PATH;
//                    String resultsPath = args.length > 2 ? args[2] : DEFAULT_RESULTS_PATH;
//                    testCompleteWorkflow(jsonFilePath, resultsPath);
//                    break;
//                case "test-install": //NO I18N
//                    String productName = args.length > 1 ? args[1] : "ManageEngine_Endpoint_Central_Setup"; //NO I18N
//                    boolean testMode = args.length > 2 && "test-mode".equalsIgnoreCase(args[2]); //NO I18N
//                    testExeInstall(productName, testMode);
//                    break;
//                case "rerun-failed": //NO I18N
//                    String testResults = args.length > 1 ? args[1] : DEFAULT_RESULTS_PATH;
//                    String extractedJson = args.length > 2 ? args[2] : DEFAULT_EXTRACTED_OUTPUT_PATH;
//                    String failedJson = args.length > 3 ? args[3] : DEFAULT_FAILED_TESTS_PATH;
//                    rerunFailedTestsCLI(testResults, extractedJson, failedJson);
//                    break;
//                default:
//                    LOGGER.warning("Unknown command: " + command); //NO I18N
//                    printUsage();
//                    break;
//            }
//        } catch (Exception e) {
//            LOGGER.log(Level.SEVERE, "Error executing command", e); //NO I18N
//        }
//    }
//
//    /**
//     * Rerun failed tests from command line
//     */
//    private static void rerunFailedTestsCLI(String resultsFile, String extractedJson, String failedJson) {
//        try {
//            LOGGER.info("=== RERUNNING FAILED TESTS ==="); //NO I18N
//            LOGGER.info("Results file: " + resultsFile); //NO I18N
//            LOGGER.info("Original extracted JSON: " + extractedJson); //NO I18N
//            LOGGER.info("Failed tests output: " + failedJson); //NO I18N
//
//            // Check if necessary files exist
//            File resultsFileObj = new File(resultsFile);
//            File extractedFileObj = new File(extractedJson);
//
//            if (!resultsFileObj.exists()) {
//                LOGGER.severe("Test results file not found: " + resultsFile); //NO I18N
//                System.exit(1);
//            }
//
//            if (!extractedFileObj.exists()) {
//                LOGGER.severe("Extracted JSON file not found: " + extractedJson); //NO I18N
//                System.exit(1);
//            }
//
//            // Create directories for output if needed
//            ensureParentDirectoriesExist(failedJson);
//
//            // Check if there are any failures
//            ResultAnalyzer analyzer = new ResultAnalyzer();
//            boolean hasFailures = analyzer.hasFailures(resultsFile);
//
//            if (!hasFailures) {
//                LOGGER.info("No failures found in test results. Nothing to rerun."); //NO I18N
//                System.exit(0);
//            }
//
//            // Extract failed test cases
//            analyzer.extractFailedTestCases(resultsFile, extractedJson, failedJson);
//
//            // Rerun the failed tests
//            testCompleteWorkflow(failedJson, resultsFile);
//
//            LOGGER.info("=== RERUNNING FAILED TESTS COMPLETED ==="); //NO I18N
//
//        } catch (Exception e) {
//            LOGGER.log(Level.SEVERE, "Error rerunning failed tests", e); //NO I18N
//            System.exit(1);
//        }
//    }
//
//    /**
//     * Get user input as a string
//     */
//    private static String getUserInput(String prompt) {
//        ConsoleOut.print(prompt);
//        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
//        try {
//            return reader.readLine();
//        } catch (IOException e) {
//            LOGGER.log(Level.SEVERE, "Error reading input", e); //NO I18N
//            return ""; //NO I18N
//        }
//    }
//
//    /**
//     * Get user choice as an integer
//     */
//    private static int getUserChoice(String prompt) {
//        while (true) {
//            try {
//                String input = getUserInput(prompt);
//                return Integer.parseInt(input);
//            } catch (NumberFormatException e) {
//                ConsoleOut.println("Invalid input. Please enter a number."); //NO I18N
//            }
//        }
//    }
//
//    /**
//     * Wait for the user to press Enter
//     */
//    private static void waitForEnter() {
//        ConsoleOut.print("\nPress Enter to continue..."); //NO I18N
//        try {
//            System.in.read();
//        } catch (IOException e) {
//            LOGGER.log(Level.SEVERE, "Error waiting for input", e); //NO I18N
//        }
//    }
//
//    /**
//     * Convert test cases between formats (Excel/CSV to JSON)
//     */
//    private static void convertTestCases(String inputFile, String outputFile) throws Exception {
//        LOGGER.info("=== CONVERTING TEST CASES ==="); //NO I18N
//        LOGGER.info("Input file: " + inputFile); //NO I18N
//        LOGGER.info("Output file: " + outputFile); //NO I18N
//
//        // Read test cases based on file extension
//        List<TestCase> testCases;
//        if (inputFile.toLowerCase().endsWith(".xlsx")) { //NO I18N
//            LOGGER.info("Reading Excel file..."); //NO I18N
//            testCases = TestCaseReader.readTestCasesFromExcel(inputFile);
//        } else if (inputFile.toLowerCase().endsWith(".csv")) { //NO I18N
//            LOGGER.info("Reading CSV file..."); //NO I18N
//            testCases = TestCaseReader.readTestCasesFromCsv(inputFile);
//        } else {
//            LOGGER.severe("Unsupported input file format. Use .xlsx or .csv"); //NO I18N
//            throw new IllegalArgumentException("Unsupported file format"); //NO I18N
//        }
//
//        LOGGER.info("Read " + testCases.size() + " test cases from input file"); //NO I18N
//
//        // Convert test cases to JSON - using the new method to exclude productName and operations
//        TestCaseReader.convertToJson(testCases, outputFile);
//
//        LOGGER.info("Successfully converted test cases to JSON format"); //NO I18N
//        LOGGER.info("Output written to: " + outputFile); //NO I18N
//    }
//
//    /**
//     * Test the LLAMA response generation
//     */
//    private static void testLLAMAResponse(String jsonFilePath, String rawOutputPath, String extractedOutputPath) throws IOException {
//        LOGGER.info("=== TESTING LLAMA RESPONSE GENERATION ==="); //NO I18N
//        LOGGER.info("JSON file: " + jsonFilePath); //NO I18N
//        LOGGER.info("Raw output: " + rawOutputPath); //NO I18N
//        LOGGER.info("Extracted output: " + extractedOutputPath); //NO I18N
//
//        // Call LLAMA client to generate response
//        LOGGER.info("Calling LLAMA client..."); //NO I18N
//        LLAMAClient.sendJsonToLLAMA(jsonFilePath, rawOutputPath, extractedOutputPath);
//
//        LOGGER.info("=== LLAMA RESPONSE GENERATION COMPLETED ==="); //NO I18N
//    }
//
//    /**
//     * Test the LLAMA response generation - legacy format
//     */
//    private static void testLLAMAResponse(String[] args) throws IOException {
//        String inputFilePath = DEFAULT_EXCEL_PATH;
//        String jsonFilePath = DEFAULT_JSON_PATH;
//        String rawOutputPath = DEFAULT_RAW_OUTPUT_PATH;
//        String extractedOutputPath = DEFAULT_EXTRACTED_OUTPUT_PATH;
//
//        if (args.length > 1) {inputFilePath = args[1];}
//        if (args.length > 2) {jsonFilePath = args[2];}
//        if (args.length > 3) {rawOutputPath = args[3];}
//        if (args.length > 4) {extractedOutputPath = args[4];}
//
//        // Ensure paths are absolute
//        jsonFilePath = ensureAbsolutePath(jsonFilePath);
//        rawOutputPath = ensureAbsolutePath(rawOutputPath);
//        extractedOutputPath = ensureAbsolutePath(extractedOutputPath);
//
//        // Ensure parent directories exist
//        ensureParentDirectoriesExist(jsonFilePath);
//        ensureParentDirectoriesExist(rawOutputPath);
//        ensureParentDirectoriesExist(extractedOutputPath);
//
//        testLLAMAResponse(jsonFilePath, rawOutputPath, extractedOutputPath);
//    }
//
//    /**
//     * Test individual operations
//     */
//    private static void testOperations(String operationType, String filePath, String fileName, String value, String productName) {
//        LOGGER.info("=== TESTING INDIVIDUAL OPERATIONS ==="); //NO I18N
//        LOGGER.info("Operation type: " + operationType); //NO I18N
//        LOGGER.info("File path: " + filePath); //NO I18N
//        LOGGER.info("File name: " + fileName); //NO I18N
//        LOGGER.info("Value: " + value); //NO I18N
//        LOGGER.info("Product name: " + productName); //NO I18N
//
//        // Create a test operation
//        Operation operation = new Operation(operationType, filePath, fileName, value, productName);
//        List<Operation> operations = new ArrayList<>();
//        operations.add(operation);
//
//        // Execute the operation
//        String result = TestExecutor.executeTestSequence("TEST-OP", operations); //NO I18N
//
//        LOGGER.info("Operation result: " + result); //NO I18N
//        LOGGER.info("=== OPERATION TESTING COMPLETED ==="); //NO I18N
//    }
//
//    /**
//     * Test the complete workflow
//     */
//    private static void testCompleteWorkflow(String jsonFilePath, String resultsPath) throws IOException {
//        LOGGER.info("=== TESTING COMPLETE WORKFLOW ==="); //NO I18N
//
//        // Ensure paths are absolute
//        jsonFilePath = ensureAbsolutePath(jsonFilePath);
//        resultsPath = ensureAbsolutePath(resultsPath);
//
//        // Call ResponseHandler to process the JSON and execute tests
//        LOGGER.info("Processing JSON response from: " + jsonFilePath); //NO I18N
//        //  ResponseHandler.handleResponse(jsonFilePath, resultsPath);
//
//        LOGGER.info("=== COMPLETE WORKFLOW TESTING COMPLETED ==="); //NO I18N
//    }
//
//    /**
//     * Test the ExeInstall functionality
//     */
//    private static void testExeInstall(String productName, boolean testMode) {
//        LOGGER.info("=== TESTING EXE INSTALL ==="); //NO I18N
//
//        // Set test mode
//        if (testMode) {
//            System.setProperty("com.me.testmode", "true"); //NO I18N
//            LOGGER.info("Running in TEST MODE - installation will be simulated"); //NO I18N
//        } else {
//            System.setProperty("com.me.testmode", "false"); //NO I18N
//            LOGGER.info("Running in PRODUCTION MODE - actual installation will be performed"); //NO I18N
//        }
//
//        LOGGER.info("Testing installation of product: " + productName); //NO I18N
//
//        // Create a proper Operation object
//        Operation exeOperation = new Operation("exe_install", new HashMap<String, String>() {{ //NO I18N
//            put("product_name", productName); // Use the actual string value you were passing //NO I18N
//        }});
//        ExeInstall exeInstaller = new ExeInstall();
//        boolean result = exeInstaller.executeOperation(exeOperation);
//
//        LOGGER.info("Installation result: " + (result ? "Success" : "Failed")); //NO I18N
//        LOGGER.info("=== EXE INSTALL TESTING COMPLETED ==="); //NO I18N
//    }
//
//    /**
//     * Ensure that a path is absolute
//     */
//    private static String ensureAbsolutePath(String path) {
//        File file = new File(path);
//        if (!file.isAbsolute()) {
//            // Get the current execution directory
//            String currentDir = System.getProperty("user.dir"); //NO I18N
//            file = new File(currentDir, path);
//        }
//        return file.getAbsolutePath();
//    }
//
//    /**
//     * Ensure that parent directories exist
//     */
//    private static void ensureParentDirectoriesExist(String path) {
//        File file = new File(path);
//        File parent = file.getParentFile();
//        if (parent != null && !parent.exists()) {
//            parent.mkdirs();
//        }
//    }
//
//    /**
//     * Print usage information
//     */
//    private static void printUsage() {
//        ConsoleOut.println("\nG.O.A.T (Generic Orchestrated Automated Testing) Tester"); //NO I18N
//        ConsoleOut.println("======================================================="); //NO I18N
//        ConsoleOut.println("\nUsage: java GOATTester <command> [options]"); //NO I18N
//        ConsoleOut.println("\nCommands:"); //NO I18N
//        ConsoleOut.println("  help                  - Show this help message"); //NO I18N
//        ConsoleOut.println("  convert-testcases     - Convert test cases between formats"); //NO I18N
//        ConsoleOut.println("    <input.xlsx|csv> <output.json>"); //NO I18N
//        ConsoleOut.println("  test-llama [options]  - Test LLAMA response generation"); //NO I18N
//        ConsoleOut.println("  test-workflow [json]  - Execute tests from extracted JSON"); //NO I18N
//        ConsoleOut.println("  test-operations <operationType> <filePath> <fileName> <value> <productName>"); //NO I18N
//        ConsoleOut.println("                        - Test individual operations"); //NO I18N
//        ConsoleOut.println("  test-install [product] [test-mode]"); //NO I18N
//        ConsoleOut.println("                        - Test the ExeInstall functionality"); //NO I18N
//        ConsoleOut.println("  rerun-failed [results] [extractedJson] [failedJson]"); //NO I18N
//        ConsoleOut.println("                        - Re-run failed tests from previous run"); //NO I18N
//        ConsoleOut.println("\nExamples:"); //NO I18N
//        ConsoleOut.println("  java GOATTester convert-testcases manual_cases/testcases.xlsx manual_cases/testcases.json"); //NO I18N
//        ConsoleOut.println("  java GOATTester test-llama manual_cases/testcases.xlsx manual_cases/testcases.json"); //NO I18N
//        ConsoleOut.println("  java GOATTester test-workflow manual_cases/extracted_response.json"); //NO I18N
//        ConsoleOut.println("  java GOATTester test-operations check_presence server_home/conf product.conf \"\" ManageEngine_Endpoint_Central_Setup"); //NO I18N
//        ConsoleOut.println("  java GOATTester test-install ManageEngine_Endpoint_Central_Setup test-mode"); //NO I18N
//        ConsoleOut.println("  java GOATTester rerun-failed logs/test_results.json manual_cases/extracted_response.json manual_cases/failed_testcases.json"); //NO I18N
//    }
//}
