package com.me;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.HashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import com.me.testcases.ServerUtils;
import com.me.testcases.TaskManagerHandler;
import com.me.util.CommonUtill;
import com.me.util.LockFileUtil;
import com.me.util.LogManager;
import com.me.testcases.BatchFileExecutor;
import com.me.testcases.CommandExecutor;
import com.me.testcases.FileEditHandler;
import com.me.testcases.FileReader;
import com.me.testcases.ApiCaseHandler;

import static com.me.testcases.ServerUtils.checkServerHome;
import static com.me.testcases.ServerUtils.getProductServerHome;
import static com.me.util.GOATCommonConstants.SERVERHOMEMAP;

/**
 * Executes test sequences based on operations
 */
public class TestExecutor {
    // Update to use framework logger
    private static final Logger LOGGER = LogManager.getLogger(TestExecutor.class.getName(), LogManager.LOG_TYPE.FW);
    
    // Current test case ID for specific logging
    private static String currentTestCaseId = null;

    /**
     * Execute a sequence of operations
     */
    public static String executeTestSequence(String testId, List<Operation> operations) {
        StringBuilder results = new StringBuilder();
        boolean allSuccessful = true;
        
        // Store current test ID for logging
        currentTestCaseId = testId;

        // Check serverHome
        boolean isServerHomeSet = checkServerHome(testId);
        if (isServerHomeSet){
            LOGGER.info("Server home is set to testid of : "+testId+" with the value of :" + SERVERHOMEMAP.get(testId+"_ServerHome"));
        }

        // Get test-specific logger
        Logger testLogger = LOGGER;

        testLogger.info("====== Starting test execution for ID: " + testId + " ======");//No I18N
        testLogger.info("Number of operations: " + operations.size());//No I18N
        
        for (Operation op : operations) {
            // Log operation details
            testLogger.info("  Operation: " + op.getOperationType());//No I18N
            testLogger.info("  File path: " + op.getFilePath());//No I18N
            testLogger.info("  File name: " + op.getFileName());//No I18N
            testLogger.info("  Value: " + op.getValue());//No I18N
            testLogger.info("  Product name: " + op.getProductName());//No I18N
            
            boolean result = executeOperation(op);
            results.append("Operation ").append(op.getOperationType());//No I18N
            
            if (result) {
                results.append(" - SUCCESSFUL\n");//No I18N
                testLogger.info("  Result: SUCCESSFUL");//No I18N
            } else {
                results.append(" - FAILED\n");//No I18N
                allSuccessful = false;
                testLogger.warning("  Result: FAILED");//No I18N
            }
        }
        
        String finalResult = allSuccessful ? "PASSED" : "FAILED";//No I18N
        results.append("Test case ").append(testId).append(": ").append(finalResult);//No I18N
        
        testLogger.info("====== Completed test execution: " + finalResult + " ======");//No I18N
        
        // Reset current test ID
        currentTestCaseId = null;
        
        return results.toString();
    }
    
    /**
     * Execute a single operation - make method static so it can be called from anywhere
     * 
     * @param operation The operation to execute
     * @return true if operation executed successfully, false otherwise
     */
    public static boolean executeOperation(Operation operation) {
        if (operation == null) {
            LOGGER.severe("Cannot execute null operation");//No I18N
            return false;
        }
        
        LOGGER.info("Executing operation: " + operation.getOperationType());//No I18N
        
        try {
            // Use the factory to execute the operation
            boolean result = OperationHandlerFactory.executeOperation(operation);
            
            // Handle result logging
            if (result) {
                LOGGER.info("Operation executed successfully: " + operation.getOperationType());//No I18N
            } else {
                LOGGER.warning("Operation execution failed: " + operation.getOperationType());//No I18N
            }
            
            return result;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing operation: " + operation.getOperationType(), e);//No I18N
            return false;
        }
    }
    
    /**
     * Execute EXE installation
     */
    private static boolean executeExeInstall(Operation op) {
        String productName = op.getProductName();
        if (productName == null || productName.isEmpty()) {
            LOGGER.warning("Product name is null or empty for exe_install operation");//No I18N
            return false;
        }
        
        return executeOperation(op);
    }
    
    /**
     * Check if a file exists
     */
    private static boolean checkFilePresence(Operation op) {
        String resolvedPath = resolveFilePath(op);
        
        if (resolvedPath == null || resolvedPath.isEmpty()) {
            LOGGER.warning("Could not resolve file path for check_presence operation");//No I18N
            return false;
        }
        
        File file = new File(resolvedPath);
        boolean exists = file.exists();
        
        LOGGER.info("Checking file presence: " + resolvedPath + " - " + (exists ? "Found" : "Not found"));//No I18N
        return exists;
    }
    
    /**
     * Check if a file does not exist
     */
    private static boolean verifyFileAbsence(Operation op) {
        String resolvedPath = resolveFilePath(op);
        
        if (resolvedPath == null || resolvedPath.isEmpty()) {
            LOGGER.warning("Could not resolve file path for verify_absence operation");//No I18N
            return false;
        }
        
        File file = new File(resolvedPath);
        boolean absent = !file.exists();
        
        LOGGER.info("Verifying file absence: " + resolvedPath + " - " + (absent ? "Absent" : "Present"));//No I18N
        return absent;
    }
    
    /**
     * Check if a value is present in a file - updated to use FileReader
     */
    private static boolean checkValuePresence(Operation op) {
        String filePath = op.getFilePath();
        String fileName = op.getFileName();
        String value = op.getValue();
        
        if ((filePath == null || filePath.isEmpty()) && (fileName == null || fileName.isEmpty())) {
            LOGGER.warning("Both file path and file name are empty for value_should_be_present operation");//No I18N
            return false;
        }
        
        if (value == null || value.isEmpty()) {
            LOGGER.warning("Value is null or empty for value_should_be_present operation");//No I18N
            return false;
        }
        
        // Construct full path
        String fullPath = resolveFilePath(op);
        
        LOGGER.info("Checking value presence in " + fullPath + ": " + value);//No I18N
        
        // Check if file exists
        if (!FileReader.checkFileExists(fullPath)) {
            LOGGER.warning("File does not exist: " + fullPath);//No I18N
            return false;
        }
        
        // Use FileReader to check if the file contains the value
        boolean found = FileReader.containsString(fullPath, value);
        
        LOGGER.info("Value " + (found ? "found" : "not found") + " in file: " + fullPath);//No I18N
        return found;
    }
    
    /**
     * Check if a value is absent from a file - updated to use FileReader
     */
    private static boolean checkValueAbsence(Operation op) {
        String filePath = op.getFilePath();
        String fileName = op.getFileName();
        String value = op.getValue();
        
        if ((filePath == null || filePath.isEmpty()) && (fileName == null || fileName.isEmpty())) {
            LOGGER.warning("Both file path and file name are empty for value_should_be_removed operation");//No I18N
            return false;
        }
        
        if (value == null || value.isEmpty()) {
            LOGGER.warning("Value is null or empty for value_should_be_removed operation");//No I18N
            return false;
        }
        
        // Construct full path
        String fullPath = resolveFilePath(op);
        
        LOGGER.info("Checking value absence in " + fullPath + ": " + value);//No I18N
        
        // If file doesn't exist, the value is technically absent
        if (!FileReader.checkFileExists(fullPath)) {
            LOGGER.warning("File does not exist: " + fullPath + ", value is considered absent");//No I18N
            return true;
        }
        
        // Use FileReader to check if the file contains the value
        boolean found = FileReader.containsString(fullPath, value);
        
        // Value should be absent, so return true if not found
        LOGGER.info("Value " + (found ? "found" : "not found") + " in file: " + fullPath);//No I18N
        return !found;
    }
    
    /**
     * Execute a task manager operation
     */
    private static boolean executeTaskManagerOperation(Operation op) {
        String action = op.getParameter("action");//No I18N
        String processName = op.getParameter("process_name");//No I18N
        String processPath = op.getParameter("process_path");//No I18N
        
        if (action == null || action.isEmpty()) {
            LOGGER.warning("Action is required for task_manager operation");//No I18N
            return false;
        }
        
        if (processName == null || processName.isEmpty()) {
            LOGGER.warning("Process name is required for task_manager operation");//No I18N
            return false;
        }
        
        LOGGER.info("Executing task manager operation: " + action + " for process: " + processName + //No I18N
                   (processPath != null && !processPath.isEmpty() ? " at path: " + processPath : ""));//No I18N
                   
        return TaskManagerHandler.executeTaskManagerOperation(action, processName, processPath);
    }
    
    /**
     * Execute a batch file
     */
    private static boolean executeBatchFile(Operation op) {
        LOGGER.info("Executing batch file operation");//No I18N
        
        String batFile = op.getParameter("bat_file");//No I18N
        String batFilePath = op.getParameter("bat_file_path");//No I18N
        
        if (batFile == null || batFile.isEmpty()) {
            LOGGER.warning("Batch file name is required for run_bat operation");//No I18N
            return false;
        }
        
        LOGGER.info("Executing batch file: " + batFile + " in path: " + batFilePath);//No I18N
        return BatchFileExecutor.executeOperation(op);
    }
    
    /**
     * Execute a system command
     */
    private static boolean executeCommand(Operation op) {
        LOGGER.info("Executing command operation");//No I18N
        
        String commandToRun = op.getParameter("command_to_run");//No I18N
        
        if (commandToRun == null || commandToRun.isEmpty()) {
            LOGGER.warning("Command to run is required for run_command operation");//No I18N
            return false;
        }
        
        LOGGER.info("Executing command: " + commandToRun);//No I18N
        return CommandExecutor.executeOperation(op);
    }
    
    /**
     * Execute a file edit operation
     */
    private static boolean executeFileEdit(Operation op) {
        LOGGER.info("Executing file edit operation");//No I18N
        
        String action = op.getParameter("action");//No I18N
        String fileName = op.getParameter("filename");//No I18N
        String filePath = op.getParameter("file_path");//No I18N
        
        if (action == null || action.isEmpty()) {
            LOGGER.warning("Action is required for file_edit operation");//No I18N
            return false;
        }
        
        if (fileName == null || fileName.isEmpty()) {
            LOGGER.warning("File name is required for file_edit operation");//No I18N
            return false;
        }
        
        LOGGER.info("Editing file: " + fileName + " with action: " + action);//No I18N
        return FileEditHandler.executeOperation(op);
    }
    
    /**
     * Execute an API test case
     */
    private static boolean executeApiCase(Operation op) {
        LOGGER.info("Executing API case operation");//No I18N
        
        String connection = op.getParameter("connection");//No I18N
        String apiToHit = op.getParameter("apiToHit");//No I18N
        String httpMethod = op.getParameter("http_method");//No I18N
        
        if (apiToHit == null || apiToHit.isEmpty()) {
            LOGGER.warning("API endpoint (apiToHit) is required for api_case operation");//No I18N
            return false;
        }
        
        LOGGER.info("API endpoint: " + apiToHit + ", method: " + httpMethod + ", connection: " + connection);//No I18N
        return ApiCaseHandler.executeOperation(op);
    }
    
    /**
     * Resolve file path with filename and handle server_home placeholder
     */
    private static String resolveFilePath(Operation op) {
        String filePath = op.getFilePath();
        String fileName = op.getFileName();
        
        if (filePath == null) {filePath = "";}
        if (fileName == null) {fileName = "";}
        
        // If both are empty, cannot resolve
        if (filePath.isEmpty() && fileName.isEmpty()) {
            LOGGER.warning("Both file path and file name are empty");//No I18N
            return "";
        }
        
        // Resolve server_home placeholder
        String resolvedPath = ServerUtils.resolvePath(filePath);
        
        // Add filename to path if present
        if (!fileName.isEmpty()) {
            // Ensure path ends with separator if not empty
            if (!resolvedPath.isEmpty() && !resolvedPath.endsWith("/") && !resolvedPath.endsWith("\\")) {
                resolvedPath += File.separator;
            }
            resolvedPath += fileName;
        }
        
        LOGGER.fine("Resolved file path: " + resolvedPath);//No I18N
        return resolvedPath;
    }

    /**
     * Execute test cases and manage results
     */
    private void executeTestCaseWithResultTracking(String testCaseId, JsonObject testCase) {
        long startTime = System.currentTimeMillis();

        // Extract the operations array
        JsonArray operations = testCase.getAsJsonArray("operations");//No I18N
        String expectedResult = testCase.has("expected_result") ? //No I18N
                               testCase.get("expected_result").getAsString() : "";//No I18N


        AtomicReference<Boolean> allSuccess = new AtomicReference<>(true);
        AtomicReference<String> errorMessage = new AtomicReference<>(null);
        StringBuilder remarksBuilder = new StringBuilder();

        String guiAppType = isGUITesting(operations);
            LOGGER.info("Is GUI Testing :: "+(guiAppType != null));
            LOGGER.info("GUI APP :: "+guiAppType);
            LOGGER.info("App Home :: "+System.getProperty("goat.app.home"));

        if (guiAppType != null){
            String serverLocation = testCase.has("server_location") ? testCase.get("server_location").getAsString() : getProductServerHome();

            JsonObject firstOp = operations.get(0).getAsJsonObject();
            JsonObject parameters = firstOp.getAsJsonObject("parameters");
            if (parameters.has("server_home")) {
                serverLocation = parameters.get("server_home").getAsString();
                LOGGER.info("Server Home Found In Parameters :: " + serverLocation);
            } else {
                LOGGER.info("Server Home Not Found In Parameters, Using Default Product Server Home");
            }
            String uniqueTestId = System.getProperty("unique.test.id");
            JsonArray systemPropertyArray = testCase.has("system_properties") ? testCase.getAsJsonArray("system_properties") : null;
            JsonArray mainArgs = testCase.has("main_args") ? testCase.getAsJsonArray("main_args") : null;
            LOGGER.log(Level.INFO,"Main Args From Json :: {0}",new Object[]{mainArgs});
            handleGUIOperations(uniqueTestId,testCaseId,operations,systemPropertyArray,mainArgs,remarksBuilder,errorMessage,allSuccess,guiAppType,serverLocation);
        }else {
            handleOperations(operations, remarksBuilder, errorMessage, allSuccess);
        }

        // Create test result
        TestResult result = new TestResult();
        result.setTestCaseId(testCaseId);
        result.setStatus(allSuccess.get() ? TestResult.STATUS_PASSED : TestResult.STATUS_FAILED);
        result.setExpectedResult(expectedResult);
        if (errorMessage.get() != null) {
            result.setError(errorMessage.get());
        }
        result.setRemarks(remarksBuilder.toString());
        result.setExecutionTime(System.currentTimeMillis() - startTime);

        // Add to result manager
        TestResultManager.addResult(testCaseId, result);
    }


    /**
     * Execute a specific operation based on its type
     */
    private boolean executeOperationByType(Operation operation) {
        if (operation == null) {
            return false;
        }
        
        String operationType = operation.getOperationType();
        LOGGER.info("Executing operation: " + operationType);//No I18N
        
        // Use the OperationHandlerFactory to execute the operation
        try {
            return OperationHandlerFactory.executeOperation(operation);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing operation", e);//No I18N
            return false;
        }
    }

    /**
     * Execute a test case from a file and write results to another file.
     *
     * @param testCaseFilePath Path to the test case file.
     * @param resultsFilePath Path to the results file.
     * @return true if execution is successful, false otherwise.
     */
    public boolean executeFromFile(String testCaseFilePath, String resultsFilePath) {
        LOGGER.info("Executing test case from file: " + testCaseFilePath);

        try {
            // Read the test case JSON from the file
            String testCaseJson = new String(Files.readAllBytes(Paths.get(testCaseFilePath)));

            // Parse the JSON into a JsonObject
            JsonObject testCaseObject = JsonParser.parseString(testCaseJson).getAsJsonObject();

            // Extract the test case ID (assuming the first key is the test case ID)
            String testCaseId = testCaseObject.keySet().iterator().next();
            JsonObject testCase = testCaseObject.getAsJsonObject(testCaseId);

            // Execute the test case and track results
            executeTestCaseWithResultTracking(testCaseId, testCase);

            // Write the results to the results file
            TestResult result = TestResultManager.getResult(testCaseId);
            if (result != null) {
                String resultJson = new GsonBuilder().setPrettyPrinting().create().toJson(result.toJsonObject());
                Files.write(Paths.get(resultsFilePath), resultJson.getBytes());
                LOGGER.info("Results written to file: " + resultsFilePath);
                return true;
            } else {
                LOGGER.warning("No results found for test case ID: " + testCaseId);
                return false;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing test case from file", e);
            return false;
        }
    }

    private String isGUITesting(JsonArray operations){
        String appType = null;
        for (int i = 0; i < operations.size(); i++) {
            JsonObject jsonObject = operations.get(i).getAsJsonObject();
            String operationType = jsonObject.get("operation_type").getAsString();
            if (operationType.equals("gui_operation")){
                appType = "Unknown";
                if (jsonObject.has("parameters") && jsonObject.getAsJsonObject("parameters").has("app_type")){
                    return jsonObject.getAsJsonObject("parameters").get("app_type").getAsString();
                }
            }
        }
        return appType;
    }

    private void handleOperations(JsonArray operations , StringBuilder remarksBuilder , AtomicReference<String> errorMessage,AtomicReference<Boolean> allSuccess){
        // Execute each operation
        for (JsonElement opElement : operations) {
            JsonObject operationJson = opElement.getAsJsonObject();
            Operation op = CommonUtill.createOperationFromJson(operationJson);



            // Start timing the operation
            op.startExecution();

            // Execute the operation
            boolean operationSuccess = op.execute();

            // End timing
            op.endExecution(operationSuccess);

            // Get results
            if (op.getRemarks() != null) {
                remarksBuilder.append(op.getRemarks()).append("\n\n"); //NO I18N
            }

            if (!operationSuccess) {
                allSuccess.set(false);
                errorMessage.set("Operation failed: " + op.getOperationType());//No I18N
                break;
            }
        }
    }

    private void handleGUIOperations(String uniqueTestId , String testCaseId , JsonArray operations ,JsonArray systemProperties,JsonArray mainArgs, StringBuilder remarksBuilder , AtomicReference<String> errorMessage, AtomicReference<Boolean> allSuccess, String appType, String serverLocation){
        GuiTestBatGenerator batGenerator = null;
        try {

            String encodedOperation = constructSerializedJsonForGUITesting(operations);
            LOGGER.info("Encoded Json :: " + encodedOperation);

            String encodedSystemProperties = null;
            if (systemProperties != null){
                encodedSystemProperties = constructSerializedJsonForGUITesting(systemProperties);
                LOGGER.info("Encoded System Properties Json :: " + encodedSystemProperties);
            }

            String encodedMainArgs = null;
            if (mainArgs != null){
                encodedMainArgs = constructSerializedJsonForGUITesting(mainArgs);
                LOGGER.info("Encoded Main Args Json :: " + encodedMainArgs);
            }

            LOGGER.info("APP Server Home :: "+serverLocation);

            batGenerator = new GuiTestBatGenerator();

            File executableBatchFile = batGenerator.generateGuiTestBat(appType, serverLocation);
            String executablePath = serverLocation+ File.separator+batGenerator.getExecutablePath();
            String executableCommand = batGenerator.getExecutableCommand();;

            Map<String,String> envMap = GuiTestBatGenerator.addEnvironmentVar(uniqueTestId,testCaseId,System.getProperty("goat.app.home"),appType,"api",encodedOperation,encodedSystemProperties,encodedMainArgs);

            LOGGER.info("Executable Batch File :: "+executableBatchFile.toString());
            LOGGER.info("Executable From Path :: "+executablePath);
            LOGGER.info("Executable Command :: "+executableCommand);

            LOGGER.info("Env Map :: "+envMap.toString());

            List<String> commands = new LinkedList<>();
            commands.add(executableCommand);

            String output = CommandExecutor.executeCommandAndGetOutput(commands,new File(executablePath),envMap);
            LOGGER.info("Out :: "+output);

            File lckFile = new File(getReportPath(uniqueTestId) + File.separator + "gui_" + uniqueTestId + ".lck");
            Thread.sleep(10 * 1000);
            while (LockFileUtil.isFileLocked(lckFile)) {
                LOGGER.info("Waiting For GUI Operation To Complete");
                Thread.sleep(10 * 1000);
            }
            LOGGER.info("GUI Operation Completed");
            Files.deleteIfExists(lckFile.toPath());

            Properties resultProp = getGUIResult(uniqueTestId);
            String status = resultProp.getProperty("Status","");
            String remarks = resultProp.getProperty("Remarks","");
            String error = resultProp.getProperty("Error","");

            LOGGER.info("GUI Test Status :: "+status);
            LOGGER.info("GUI Test Remarks :: "+remarks);
            LOGGER.info("GUI Test Error :: "+error);

            allSuccess.set(status.trim().equalsIgnoreCase("SUCCESS"));
            remarksBuilder.append(remarks);
            errorMessage.set(error);

        }catch (Exception e){
            allSuccess.set(false);
            errorMessage.set("Exception While Executing GUI Cases "+e);
            LOGGER.warning("Exception While Executing GUI Cases "+e);
        }finally {
            if (batGenerator != null){
                batGenerator.cleanUpFiles();
            }
        }
    }

    private Properties getGUIResult(String uniqueTestId) throws IOException {
       File resultProps = new File(getReportPath(uniqueTestId)+File.separator+"gui_result.props");
       Properties properties = new Properties();
       if (resultProps.exists()) {
           LOGGER.info("Loading GUI Results :: "+resultProps.toString());
           properties.load(new java.io.FileReader(resultProps));
       }
       return properties;
    }

    private String getReportPath(String uniqueTestId){
        return System.getProperty("goat.app.home") + File.separator+"test_status"+File.separator+uniqueTestId;
    }

    private String constructSerializedJsonForGUITesting(JsonArray jsonArray){
        LOGGER.info("GUI JSON ::");
        LOGGER.info(jsonArray.toString());

        return Base64.getEncoder().encodeToString(jsonArray.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static int getActiveUserSessionId() {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "query session");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Active")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 3) {
                        try {
                            return Integer.parseInt(parts[2]); // ID is typically at index 2
                        } catch (NumberFormatException e) {
                            // Ignore malformed lines
                        }
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    private static boolean copyPsExe(String executeLoc) {
        try {
            File psExe = new File(System.getProperty("goat.app.home") + File.separator + "bin" + File.separator + "PsExec.exe");
            if (!psExe.exists()) {
                throw new Exception("Psexec.exe Not Exist");
            }

            File destinationPath = new File(executeLoc + File.separator + psExe.getName());
            if (!destinationPath.exists()) {
                destinationPath.createNewFile();
            }

            Files.copy(psExe.toPath(), destinationPath.toPath(), StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Psexec Exe Copied Successfully");
            return true;
        }catch (Exception e){
            LOGGER.warning("Exception While Copying Psexec Exe");
            return false;
        }
    }

    private static boolean deletePsExe(String executeLoc) {
        try {
            File destinationPath = new File(executeLoc + File.separator + "PsExec.exe");
            if (!destinationPath.exists()) {
                destinationPath.createNewFile();
            }

            Files.delete(destinationPath.toPath());
            LOGGER.info("Psexec Exe Deleted Successfully");
            return true;
        }catch (Exception e){
            LOGGER.warning("Exception While Deleting Psexec Exe");
            return false;
        }
    }

//    static void processNoteParameters(Operation op, String result) {
//        Map<String, String> noteParams = op.getNoteParams();
//        if (noteParams.isEmpty()) return;
//
//        for (Map.Entry<String, String> entry : noteParams.entrySet()) {
//            String variableName = entry.getKey();
//            String extractionPattern = entry.getValue();
//
//            // Extract value using the pattern
//            String extractedValue = extractValueUsingPattern(result, extractionPattern);
//            if (extractedValue != null) {
//                TestVariableManager.setVariable(variableName, extractedValue);
//            }
//        }
//    }
//
//    private static String extractValueUsingPattern(String input, String pattern) {
//        // Support different extraction methods based on pattern prefix
//        if (pattern.startsWith("regex:")) {
//            return extractWithRegex(input, pattern.substring(6));
//        } else if (pattern.startsWith("json:")) {
//            return extractWithJsonPath(input, pattern.substring(5));
//        } else if (pattern.startsWith("xml:")) {
//            return extractWithXPath(input, pattern.substring(4));
//        } else {
//            // Default to simple substring extraction
//            return extractWithSimplePattern(input, pattern);
//        }
//    }
}
