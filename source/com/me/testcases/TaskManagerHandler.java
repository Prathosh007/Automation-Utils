package com.me.testcases;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.me.Operation;
import com.me.ResolveOperationParameters;
import com.me.util.LogManager;
import com.me.util.ProcessScanner;
import com.me.util.ProcessScanner.ProcessInfo;

import static com.me.testcases.DataBaseOperationHandler.saveNote;

/**
 * Handles task manager operations like checking if a process is running or killing processes
 */
public class TaskManagerHandler {
    private static final Logger LOGGER = LogManager.getLogger(TaskManagerHandler.class, LogManager.LOG_TYPE.FW);

    // Constants for expectations
    private static final String EXPECT_RUNNING = "processrunning"; //No I18N
    private static final String EXPECT_NOT_RUNNING = "processnotrunning"; //No I18N

    /**
     * Execute a task manager operation
     *
     * @param op The operation containing task manager parameters
     * @return true if the operation was successful, false otherwise
     */
    public static boolean executeOperation(Operation op) {
        if (op == null) {
            LOGGER.severe("Operation is null"); //No I18N
            return false;
        }

        op = ResolveOperationParameters.resolveOperationParameters(op);

        // Get action parameter (case insensitive)
        String action = op.getParameter("action"); //No I18N
        if (action == null || action.isEmpty()) {
            LOGGER.warning("Action is required for task_manager operation"); //No I18N
            return false;
        }

        String processName = op.getParameter("process_name"); //No I18N
        String processPath = op.getParameter("process_path"); //No I18N

        // Get expectation (optional, defaults to expecting process to be running)
        String expectation = op.getParameter("expect"); //No I18N

        LOGGER.info("Executing task_manager operation: " + action ); //No I18N

        // Determine which action to execute
        switch (action.toLowerCase()) {
            case "verify_process": //No I18N
                return verifyProcess(processName, processPath, expectation, op);

            case "kill_process": //No I18N
                return killProcess(processName, processPath, op);

            case "list_processes": //No I18N
                return listAllProcesses(op);

            case "verify_process_property": //No I18N
                String searchType = op.getParameter("search_type"); //No I18N
                String searchTypeValue = op.getParameter("search_type_value"); //No I18N
                String expectedType = op.getParameter("expected_type"); //No I18N
                String expectedTypeValue = op.getParameter("expected_type_value"); //No I18N
                String note = op.getParameter("note"); //No I18N
                return verifyProcessProperty(searchType, searchTypeValue, expectedType, expectedTypeValue, note, op);

            default:
                LOGGER.warning("Unsupported task_manager action: " + action); //No I18N
                op.setRemarks("Unsupported task_manager action: " + action); //No I18N
                return false;
        }
    }

    /**
     * Verify if a process is running
     */
    private static boolean verifyProcess(String processName, String processPath, String expectation, Operation op) {
        try {
            if (expectation == null || expectation.isEmpty()) {
                expectation = "processRunning";
            }

            StringBuilder remarkBuilder = new StringBuilder();

            if (processName == null && (processPath == null || processPath.isEmpty())) {
                LOGGER.warning("FAILED: Either process name or process path must be provided");
                remarkBuilder.append("FAILED: Either process name or process path must be provided\n");
                op.setRemarks(remarkBuilder.toString());
                return false;
            }

            if ((processName == null || processName.isEmpty()) && processPath != null && !processPath.isEmpty()) {
                File pathFile = new File(processPath);
                String extractedName = pathFile.getName();
                if (!extractedName.isEmpty()) {
                    processName = extractedName;
                    LOGGER.info("Extracted process name from path: " + processName);
                }
            }

            String portParam = op.getParameter("port");
            Integer port = null;
            if (portParam != null && !portParam.isEmpty()) {
                try {
                    port = Integer.parseInt(portParam);
                    LOGGER.info("Verifying process with port: " + port);
                } catch (NumberFormatException e) {
                    LOGGER.warning("FAILED: Invalid port number: " + portParam);
                    remarkBuilder.append("FAILED: Invalid port number: ").append(portParam).append("\n");
                    op.setRemarks(remarkBuilder.toString());
                    return false;
                }
            }

            String resolvedPath = null;
            if (processPath != null && !processPath.isEmpty()) {
                boolean pathIncludesProcessName = false;
                if (processName != null && !processName.isEmpty()) {
                    String normalizedPath = normalizePath(processPath);
                    String normalizedProcessName = processName.toLowerCase();
                    pathIncludesProcessName = normalizedPath.toLowerCase().endsWith(normalizedProcessName) ||
                            normalizedPath.toLowerCase().endsWith(normalizedProcessName + ".exe");
                }
                if (pathIncludesProcessName) {
                    resolvedPath = processPath;
                    LOGGER.info("Process path already includes process name: " + resolvedPath);
                } else {
                    resolvedPath = constructExpectedProcessPath(processName, processPath);
                    LOGGER.info("Constructed complete process path: " + resolvedPath);
                }
                if (resolvedPath.contains("server_home")) {
                    String originalPath = resolvedPath;
                    resolvedPath = ServerUtils.resolvePath(resolvedPath);
                    LOGGER.info("Resolved server_home in path: " + originalPath + " → " + resolvedPath);
                }
            }

            ProcessScanner scanner = new ProcessScanner();
            List<ProcessInfo> matchingProcesses = new ArrayList<>();
            String comparisonType = "Name";

            if (port != null) {
                comparisonType = "Port";
                ProcessInfo processUsingPort = scanner.findProcessByPort(port);
                if (processUsingPort != null) {
                    boolean nameMatches = processName != null && processUsingPort.getName().equalsIgnoreCase(processName);
                    boolean pathMatches = resolvedPath != null && compareProcessPaths(processUsingPort.getExecutablePath(), processName, resolvedPath);
                    String processNameLogString = processName != null ? ", name: " + processName + (nameMatches ? " (matched)" : " (not matched)") : "";
                    String processPathLogString = resolvedPath != null ? ", path: " + resolvedPath + (pathMatches ? " (matched)" : " (not matched)") : "";
                    if ((processName == null || nameMatches) && (resolvedPath == null || pathMatches)) {
                        matchingProcesses.add(processUsingPort);
                        LOGGER.info("Process found using port: " + port + processNameLogString + processPathLogString);
                        remarkBuilder.append("Process found using port: ").append(port).append(processNameLogString).append(processPathLogString).append("\n");
                    } else {
                        LOGGER.info("Process found using port: " + port + processNameLogString + processPathLogString + " but did not match all criteria");
                        remarkBuilder.append("Process found using port: ").append(port).append(processNameLogString).append(processPathLogString).append(" but did not match all criteria\n");
                    }
                } else {
                    LOGGER.info("No process found using port: " + port);
                    remarkBuilder.append("No process found using port: ").append(port).append("\n");
                }
            } else {
                List<ProcessInfo> processesByName = scanner.getProcessesByName(processName);
                LOGGER.info("Found " + processesByName.size() + " process(es) with name: " + processName);
                if (resolvedPath != null && !resolvedPath.isEmpty()) {
                    comparisonType = "Path";
                    for (ProcessInfo process : processesByName) {
                        if (compareProcessPaths(process.getExecutablePath(), processName, resolvedPath)) {
                            matchingProcesses.add(process);
                        }
                    }
                    LOGGER.info("Filtered processes by path, found: " + matchingProcesses.size());
                } else {
                    matchingProcesses = processesByName;
                }
            }

            boolean processDetermined = !matchingProcesses.isEmpty();
            boolean result = expectation.equalsIgnoreCase(EXPECT_RUNNING) == processDetermined;

            remarkBuilder.append("Verification Result: ").append(result ? "PASSED" : "FAILED").append("\n");
            remarkBuilder.append("Comparison Type: ").append(comparisonType).append("\n");

            if (comparisonType.equals("Port")) {
                remarkBuilder.append("Expected Port: ").append(port != null ? port : "none").append("\n");
                remarkBuilder.append("Actual Port: ").append(processDetermined ? port : "none").append("\n");
            } else if (comparisonType.equals("Path")) {
                remarkBuilder.append("Expected Path: ").append(resolvedPath != null ? resolvedPath.replace('\\', '/') : "none").append("\n");
                remarkBuilder.append("Actual Path: ").append(processDetermined && matchingProcesses.get(0).getExecutablePath() != null
                        ? matchingProcesses.get(0).getExecutablePath().replace('\\', '/') : "none").append("\n");
            } else {
                remarkBuilder.append("Expected Name: ").append(processName != null ? processName : "none").append("\n");
                remarkBuilder.append("Actual Name: ").append(processDetermined && matchingProcesses.get(0).getName() != null
                        ? matchingProcesses.get(0).getName() : "none").append("\n");
            }

            if (result) {
                remarkBuilder.append("Process is running as expected.\n");
            } else {
                remarkBuilder.append("Reason for Failure:\n");
                if (comparisonType.equals("Port")) {
                    remarkBuilder.append("No process found using port ").append(port).append("\n");
                } else if (comparisonType.equals("Path")) {
                    remarkBuilder.append("No process found with name '").append(processName)
                            .append("' at path '").append(resolvedPath != null ? resolvedPath.replace('\\', '/') : "none").append("'\n");
                } else {
                    remarkBuilder.append("No process found with name '").append(processName).append("'\n");
                }
                remarkBuilder.append("Process is not running as expected.\n");
            }

            op.setRemarks(remarkBuilder.toString());
            op.setOutputValue(result ?
                    (processDetermined ? "Process is running" : "Process is not running") :
                    "Verification failed");

            return result;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error verifying process", e);
            StringBuilder remarkBuilder = new StringBuilder();
            remarkBuilder.append("Error verifying process: ").append(processName).append("\n");
            remarkBuilder.append("Exception: ").append(e.getMessage()).append("\n");
            remarkBuilder.append("FAILED: Verification error occurred\n");
            op.setRemarks(remarkBuilder.toString());
            return false;
        }
    }


    /**
     * Verify or extract a process property based on a search criteria
     *
     * @param searchType Type of search criteria (pid, port, path, name)
     * @param searchTypeValue Value of the search criteria
     * @param expectedType Type of property to verify (pid, port, path, name, memory, threads, user, etc.)
     * @param expectedTypeValue Expected value of the property (optional if note is provided)
     * @param note Parameter name to store the extracted value (optional)
     * @param op Operation object to store results
     * @return true if verification passed or property was successfully extracted
     */
    private static boolean verifyProcessProperty(String searchType, String searchTypeValue,
                                                 String expectedType, String expectedTypeValue,
                                                 String note, Operation op) {
        try {
            if (searchType == null || searchType.isEmpty() || searchTypeValue == null || searchTypeValue.isEmpty()) {
                LOGGER.warning("Search type and value must be provided"); //No I18N
                op.setRemarks("Search type and value must be provided");
                return false;
            }

            if (expectedType == null || expectedType.isEmpty()) {
                LOGGER.warning("Expected type must be provided"); //No I18N
                op.setRemarks("Expected type must be provided");
                return false;
            }

            if (expectedTypeValue == null && note == null) {
                LOGGER.warning("Either expected type value or note must be provided"); //No I18N
                op.setRemarks("Either expected type value or note must be provided");
                return false;
            }

            // Convert to lowercase for case-insensitive comparison
            searchType = searchType.toLowerCase();
            expectedType = expectedType.toLowerCase();

            LOGGER.info("Verifying process property: searchType=" + searchType +
                    ", searchTypeValue=" + searchTypeValue + ", expectedType=" + expectedType +
                    (expectedTypeValue != null ? ", expectedTypeValue=" + expectedTypeValue : ""));

            // Create ProcessScanner
            ProcessScanner scanner = new ProcessScanner();

            // Find the process based on search criteria
            List<ProcessInfo> matchingProcesses = new ArrayList<>();
            StringBuilder remarkBuilder = new StringBuilder();
            remarkBuilder.append("Process property verification:\n");
            remarkBuilder.append("Search criteria: " + searchType + " = " + searchTypeValue + "\n");
            remarkBuilder.append("Property to verify: " + expectedType + "\n");
            if (expectedTypeValue != null) {
                remarkBuilder.append("Expected value: " + expectedTypeValue + "\n");
            }
            remarkBuilder.append("\n");

            // Find processes based on search type
            switch (searchType) {
                case "pid": //No I18N
                    try {
                        int pid = Integer.parseInt(searchTypeValue);
                        ProcessInfo process = scanner.getProcessInfoByPid(pid);
                        if (process != null) {
                            matchingProcesses.add(process);
                        }
                        remarkBuilder.append("Found " + matchingProcesses.size() + " process(es) with PID " + pid + "\n");
                    } catch (NumberFormatException e) {
                        LOGGER.warning("Invalid PID: " + searchTypeValue); //No I18N
                        op.setRemarks("Invalid PID: " + searchTypeValue);
                        return false;
                    }
                    break;

                case "port": //No I18N
                    try {
                        int port = Integer.parseInt(searchTypeValue);
                        ProcessInfo process = scanner.findProcessByPort(port);
                        if (process != null) {
                            matchingProcesses.add(process);
                        }
                        remarkBuilder.append("Found " + matchingProcesses.size() + " process(es) using port " + port + "\n");
                    } catch (NumberFormatException e) {
                        LOGGER.warning("Invalid port: " + searchTypeValue); //No I18N
                        op.setRemarks("Invalid port: " + searchTypeValue);
                        return false;
                    }
                    break;

                case "path": //No I18N
                    // Find processes by path - use case-insensitive path matching
                    List<ProcessInfo> allProcesses = getAllProcesses(scanner);
                    String normalizedSearchPath = normalizePath(searchTypeValue).toLowerCase();
                    LOGGER.info("Searching for processes with path: " + normalizedSearchPath);

                    for (ProcessInfo process : allProcesses) {
                        String processPath = process.getExecutablePath();
                        if (processPath != null && !processPath.isEmpty()) {
                            String normalizedProcessPath = normalizePath(processPath).toLowerCase();
                            LOGGER.info("Comparing with process: PID=" + process.getPid() + ", Path=" + normalizedProcessPath);

                            if (normalizedProcessPath.equals(normalizedSearchPath) ||
                                    normalizedProcessPath.contains(normalizedSearchPath)) {
                                LOGGER.info("Path match found: " + process.getPid());
                                matchingProcesses.add(process);

                                if (note != null && !note.isEmpty()) {
                                    // If note parameter is provided, save PID to it
                                    saveNote(op, process.getPid());
                                }
                            }
                        }
                    }
                    remarkBuilder.append("Found " + matchingProcesses.size() + " process(es) at path containing '" +
                            searchTypeValue + "'\n");
                    break;

                case "name": //No I18N
                    matchingProcesses = ProcessScanner.getProcessesByName(searchTypeValue);
                    remarkBuilder.append("Found " + matchingProcesses.size() + " process(es) named '" +
                            searchTypeValue + "'\n");
                    LOGGER.info("Found " + matchingProcesses.size() + " process(es) named '" + searchTypeValue + "'"); //No I18N
                    break;

                case "user": //No I18N
                    // Find processes by user
                    List<ProcessInfo> processes = getAllProcesses(scanner);
                    for (ProcessInfo process : processes) {
                        String user = getProcessUserById(process.getProcessId());
                        if (user != null && user.toLowerCase().contains(searchTypeValue.toLowerCase())) {
                            matchingProcesses.add(process);
                        }
                    }
                    remarkBuilder.append("Found " + matchingProcesses.size() + " process(es) running as user '" +
                            searchTypeValue + "'\n");
                    break;

                default:
                    LOGGER.warning("Unsupported search type: " + searchType); //No I18N
                    op.setRemarks("Unsupported search type: " + searchType);
                    return false;
            }

            if (matchingProcesses.isEmpty()) {
                remarkBuilder.append("No processes found matching the search criteria\n");
                op.setRemarks(remarkBuilder.toString());
                return false;
            }

            // List found processes
            remarkBuilder.append("\nFound processes:\n");
            for (ProcessInfo process : matchingProcesses) {
                remarkBuilder.append("PID: ").append(process.getProcessId())
                        .append(", Name: ").append(process.getName())
                        .append(", Path: ").append(process.getExecutablePath())
                        .append("\n");
            }

            // Verify or extract the expected property for each process
            boolean overallResult = false;
            String extractedValue = null;

            for (ProcessInfo process : matchingProcesses) {
                // Extract the property to verify
                switch (expectedType) {
                    case "pid": //No I18N
                        extractedValue = process.getProcessId();
                        break;

                    case "name": //No I18N
                        extractedValue = process.getName();
                        break;

                    case "path": //No I18N
                        extractedValue = process.getExecutablePath();
                        break;

                    case "port": //No I18N
                        Set<Integer> ports = process.getPorts();
                        if (ports == null || ports.isEmpty()) {
                            // Get only TCP listening ports
                            ports = getTCPListeningPorts(process.getProcessId());
                            process.setPorts(ports);
                        }

                        if (ports != null && !ports.isEmpty()) {
                            extractedValue = ports.toString();
                        } else {
                            extractedValue = "Unknown";
                        }
                        break;

                    case "memory": //No I18N
                        long memory = getProcessMemory(process.getProcessId());
                        extractedValue = memory > 0 ? formatMemorySize(memory) : "Unknown";
                        overallResult = checkMemory(op, expectedTypeValue, remarkBuilder, memory, true);
                        break;

                    case "threads": //No I18N
                        int threadCount = getProcessThreadCount(process.getProcessId());
                        extractedValue = threadCount > 0 ? String.valueOf(threadCount) : "Unknown";
                        break;

                    case "get_instance": //No I18N
                        // Get the number of instances running with the same name
                        String processName = process.getName();
                        List<ProcessInfo> sameNameProcesses = ProcessScanner.getProcessesByName(processName);
                        int instanceCount = sameNameProcesses.size();

                        extractedValue = String.valueOf(instanceCount);
                        break;

                    case "user": //No I18N
                        extractedValue = getProcessUserById(process.getProcessId());
                        break;

                    case "cpu": //No I18N
                        double cpuUsage = getProcessCpuUsage(process.getProcessId());
                        extractedValue = cpuUsage >= 0 ? String.format("%.2f%%", cpuUsage) : "Unknown";
                        break;

                    case "starttime": //No I18N
                        String startTime = getProcessStartTime(process.getProcessId());
                        extractedValue = startTime != null ? startTime : "Unknown";
                        break;

                    default:
                        LOGGER.warning("Unsupported expected type: " + expectedType); //No I18N
                        op.setRemarks("Unsupported expected type: " + expectedType);
                        return false;
                }

                // If multiple processes match, use the first one that matches the expected value
                if (!expectedType.equals("memory")) {
                    if (expectedTypeValue != null) {
                        boolean matchesExpected = false;

                        // Compare extracted value with expected value
                        if (extractedValue != null) {
                            if (expectedType.equals("port")) {
                                // Special case for ports - check if the expected port is in the set
                                matchesExpected = extractedValue.contains(expectedTypeValue);
                            } else {
                                // Case-insensitive comparison for string values
                                matchesExpected = extractedValue.toLowerCase().contains(expectedTypeValue.toLowerCase());
                            }
                        }

                        remarkBuilder.append("\nProcess PID " + process.getProcessId() + ":\n");
                        remarkBuilder.append("  " + expectedType + " = " + extractedValue + "\n");
                        remarkBuilder.append("  Matches expected value: " + (matchesExpected ? "YES" : "NO") + "\n");

                        if (matchesExpected) {
                            overallResult = true;
                            break; // Found a matching process
                        }
                    } else {
                        if (extractedValue != null && !extractedValue.equals("Unknown")) {
                            // If no expected value, just extract and store the value
                            overallResult = true;
                            break; // Use the first process found
                        }
                    }
                }
            }

            // Store extracted value in note if provided
            if (note != null && !note.isEmpty() && extractedValue != null) {
                saveNote(op,extractedValue);
                op.setParameter(note, extractedValue);
                remarkBuilder.append("\nStored value '" + extractedValue + "' in parameter: " + note + "\n");
            }

            // Set final result
            if (expectedTypeValue != null) {
                remarkBuilder.append("\nVerification result: " + (overallResult ? "PASSED" : "FAILED") + "\n");
            } else {
                remarkBuilder.append("\nProperty extraction result: " + (overallResult ? "PASSED" : "FAILED") + "\n");
            }

            op.setRemarks(remarkBuilder.toString());
            op.setOutputValue(extractedValue != null ? extractedValue : "");

            return overallResult;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error verifying process property", e); //No I18N
            op.setRemarks("Error verifying process property: " + e.getMessage());
            return false;
        }
    }

    private static Set<Integer> getTCPListeningPorts(String pid) {
        Set<Integer> ports = new HashSet<>();
        try {
            // Run netstat command to get port information for this PID
            String command = "netstat -ano | findstr " + pid;
            LOGGER.info("Executing command: " + command);

            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                StringBuilder output = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    LOGGER.info("Command output: " + line);
                    output.append(line).append("\n");

                    // Only consider TCP LISTENING connections
                    if (line.contains("TCP")) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 2) {
                            String localAddress = parts[1];
                            int portIndex = localAddress.lastIndexOf(':');
                            if (portIndex > 0) {
                                try {
                                    int port = Integer.parseInt(localAddress.substring(portIndex + 1));
                                    ports.add(port);
                                    break;
                                } catch (NumberFormatException e) {
                                    // Skip if port is not a number
                                    LOGGER.warning("Invalid port number in output: " + localAddress.substring(portIndex + 1));
                                }
                            }
                        }
                    }
                }

                LOGGER.info("Command output length: " + output.length() + " bytes");
                LOGGER.info("Command output sample: " + output.substring(0, Math.min(100, output.length())));
            }

            LOGGER.info("Found " + ports.size() + " TCP listening ports for PID " + pid + ": " + ports);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting TCP listening ports for PID " + pid, e);
        }
        return ports;
    }

    private static boolean checkMemory(Operation op, String expectedTypeValue, StringBuilder remarkBuilder, long memory, boolean matchesExpected) {
        // Store the raw memory value for comparisons
        long memoryBytes = memory;

        // Get comparison operator if provided
        String comparisonOperator = op.getParameter("comparison_operator"); // "greater_than", "less_than", "equal"

        if (expectedTypeValue != null) {
            try {
                // Parse expected memory value - support both raw bytes and formatted strings
                long expectedMemory;
                if (expectedTypeValue.contains(" ")) {
                    // Parse formatted memory string like "10.5 MB"
                    expectedMemory = parseFormattedMemorySize(expectedTypeValue);
                } else {
                    // Assume raw bytes
                    expectedMemory = Long.parseLong(expectedTypeValue);
                }
                LOGGER.info("Expected memory: " + expectedMemory + " bytes" + "    Actual memory :"+memory); //No I18N

                boolean comparisonResult;
                if (comparisonOperator != null) {
                    switch (comparisonOperator.toLowerCase()) {
                        case "greater_than":
                            comparisonResult = memoryBytes > expectedMemory;
                            remarkBuilder.append("\nMemory comparison: " + formatMemorySize(memoryBytes) +
                                    " > " + formatMemorySize(expectedMemory) + " = " + comparisonResult);
                            break;
                        case "less_than":
                            comparisonResult = memoryBytes < expectedMemory;
                            remarkBuilder.append("\nMemory comparison: " + formatMemorySize(memoryBytes) +
                                    " < " + formatMemorySize(expectedMemory) + " = " + comparisonResult);
                            break;
                        default: // Default to equals
                            comparisonResult = memoryBytes == expectedMemory;
                            remarkBuilder.append("\nMemory comparison: " + formatMemorySize(memoryBytes) +
                                    " = " + formatMemorySize(expectedMemory) + " = " + comparisonResult);
                            break;
                    }
                } else {
                    // Default to equality check if no operator provided
                    comparisonResult = memoryBytes == expectedMemory;
                    remarkBuilder.append("\nMemory comparison: " + formatMemorySize(memoryBytes) +
                            " = " + formatMemorySize(expectedMemory) + " = " + comparisonResult);
                }

                // Use comparison result instead of direct equality
                matchesExpected = comparisonResult;
            } catch (NumberFormatException e) {
                LOGGER.warning("Invalid memory value format: " + expectedTypeValue);
                matchesExpected = false;
            }
        }
        return matchesExpected;
    }

    /**
     * Parse a formatted memory size like "10.5 MB" into bytes
     *
     * @param formattedSize String representation of memory size
     * @return Memory size in bytes
     */
    private static long parseFormattedMemorySize(String formattedSize) {
        String[] parts = formattedSize.trim().split("\\s+");
        if (parts.length != 2) {
            throw new NumberFormatException("Invalid memory format: " + formattedSize);
        }

        double value = Double.parseDouble(parts[0]);
        String unit = parts[1].toUpperCase();

        switch (unit) {
            case "B":
                return (long) value;
            case "KB":
                return (long) (value * 1024);
            case "MB":
                return (long) (value * 1024 * 1024);
            case "GB":
                return (long) (value * 1024 * 1024 * 1024);
            case "TB":
                return (long) (value * 1024 * 1024 * 1024 * 1024);
            default:
                throw new NumberFormatException("Unknown memory unit: " + unit);
        }
    }


    /**
     * Get all running processes as a list of ProcessInfo objects
     *
     * @param scanner ProcessScanner to use for process information retrieval
     * @return List of all running processes
     */
    private static List<ProcessInfo> getAllProcesses(ProcessScanner scanner) {
        List<ProcessInfo> processes = new ArrayList<>();
        try {
            // Refresh the scanner to get up-to-date process information
            LOGGER.info("Refreshing process list");
            scanner.refresh();

            // Get the process list from the scanner
            processes = scanner.getProcessList();

            LOGGER.info("Found " + processes.size() + " processes with path information");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting processes with path info", e);
        }

        return processes;
    }

    /**
     * Get memory usage for a process
     *
     * @param pid Process ID
     * @return Memory usage in bytes or -1 if unknown
     */
    private static long getProcessMemory(String pid) {
        try {
            String command = "powershell -Command \"Get-Process -Id " + pid + " | Select-Object -ExpandProperty WorkingSet64\"";
            String output = runCommandAndGetOutput(command);

            String trimmedOutput = output.trim();
            if (!trimmedOutput.isEmpty()) {
                try {
                    return Long.parseLong(trimmedOutput);
                } catch (NumberFormatException e) {
                    LOGGER.warning("Invalid memory value: " + trimmedOutput);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting memory for process " + pid, e);
        }
        return -1;
    }


    /**
     * Get thread count for a process
     *
     * @param pid Process ID
     * @return Thread count or -1 if unknown
     */
    private static int getProcessThreadCount(String pid) {
        try {
            String command = "powershell -Command \"(Get-Process -Id " + pid + ").Threads.Count\"";
            String output = runCommandAndGetOutput(command);

            String trimmedOutput = output.trim();
            if (!trimmedOutput.isEmpty()) {
                try {
                    return Integer.parseInt(trimmedOutput);
                } catch (NumberFormatException e) {
                    LOGGER.warning("Invalid thread count: " + trimmedOutput);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting thread count for process " + pid, e);
        }
        return -1;
    }


    /**
     * Get user running a process
     *
     * @param pid Process ID
     * @return Username or null if unknown
     */
    private static String getProcessUserById(String pid) {
        try {
            String command = "tasklist /FI \"PID eq " + pid + "\" /V /FO CSV";
            LOGGER.info("Running command to get user for PID " + pid + ": " + command); //No I18N
            String output = runCommandAndGetOutput(command);
            return extractUserFromTasklist(output);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting user for process " + pid, e); //No I18N
            return null;
        }
    }

    /**
     * Get CPU usage for a process
     *
     * @param pid Process ID
     * @return CPU usage percentage or -1 if unknown
     */
    private static double getProcessCpuUsage(String pid) {
        try {
            // Get CPU usage as percentage
            String command = "powershell -Command \"Get-Process -Id " + pid + " | Select-Object -ExpandProperty CPU\"";
            String output = runCommandAndGetOutput(command);

            String trimmedOutput = output.trim();
            if (!trimmedOutput.isEmpty() && !trimmedOutput.equalsIgnoreCase("")) {
                try {
                    // CPU time in seconds, convert to approximate percentage
                    double cpuTime = Double.parseDouble(trimmedOutput);
                    return cpuTime; // Return total CPU time in seconds
                } catch (NumberFormatException e) {
                    LOGGER.warning("Invalid CPU value: " + trimmedOutput);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting CPU usage for process " + pid, e);
        }
        return -1;
    }


    /**
     * Get start time for a process
     *
     * @param pid Process ID
     * @return Start time string or null if unknown
     */
    private static String getProcessStartTime(String pid) {
        try {
            String command = "powershell -Command \"Get-Process -Id " + pid + " | Select-Object -ExpandProperty StartTime | Get-Date -Format 'yyyy-MM-dd HH:mm:ss'\"";
            String output = runCommandAndGetOutput(command);

            String trimmedOutput = output.trim();
            if (!trimmedOutput.isEmpty()) {
                return trimmedOutput;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting start time for process " + pid, e);
        }
        return null;
    }



    /**
     * Construct complete expected process path if needed
     * If the provided path is a directory (doesn't end with process name),
     * append the process name to create the full path
     *
     * @param processName The process executable name
     * @param providedPath The path provided in the test case
     * @return The complete expected path including process name
     */
    private static String constructExpectedProcessPath(String processName, String providedPath) {
        if (providedPath == null) {
            return null;
        }

        // Normalize path separators
        String normalizedPath = normalizePath(providedPath);

        // Resolve server_home placeholder if present
        if (normalizedPath.contains("server_home")) { //NO I18N
            normalizedPath = ServerUtils.resolvePath(normalizedPath);
        }

        // Check if the path already ends with the process name
        String normalizedProcessName = normalizePath(processName);

        // Compare the last part of the path with the process name
        if (!normalizedPath.endsWith(File.separator + normalizedProcessName) &&
                !normalizedPath.endsWith(normalizedProcessName)) {

            // If path doesn't end with process name, append it
            if (!normalizedPath.endsWith(File.separator)) {
                normalizedPath += File.separator;
            }
            normalizedPath += normalizedProcessName;
            LOGGER.info("Constructed complete process path: " + normalizedPath); //NO I18N
        }

        return normalizedPath;
    }

    /**
     * Compare process paths strictly, ensuring false positives are eliminated
     *
     * @param actualProcessPath The actual process path from running process
     * @param expectedPath The expected path specified in test case
     * @return true if paths match, false otherwise
     */
    private static boolean compareProcessPaths(String actualProcessPath, String processName, String expectedPath) {
        if (actualProcessPath == null || expectedPath == null) {
            return false;
        }

        // Normalize paths for comparison
        String normalizedActual = normalizePath(actualProcessPath);

        // Construct complete expected path if it's just a directory
        String normalizedExpected = constructExpectedProcessPath(processName, expectedPath);

        // For remarks consistency - store both paths with same slash style for display
        String displayActual = normalizedActual.replace('\\', '/'); //NO I18N
        String displayExpected = normalizedExpected.replace('\\', '/'); //NO I18N

        LOGGER.info("Comparing process paths:"); //NO I18N
        LOGGER.info("  Actual path: " + displayActual); //NO I18N
        LOGGER.info("  Expected path: " + displayExpected); //NO I18N

        // Use strict path comparison that checks each component
        return pathsMatchStrict(normalizedActual, normalizedExpected);
    }

    /**
     * Perform strict path comparison by verifying each path component
     *
     * @param actualPath The actual path to verify
     * @param expectedPath The expected path to match against
     * @return true if paths match strictly, false otherwise
     */
    private static boolean pathsMatchStrict(String actualPath, String expectedPath) {
        if (actualPath == null || expectedPath == null) {
            return false;
        }

        // Convert paths to Path objects for better comparison
        Path actualPathObj = Paths.get(actualPath);
        Path expectedPathObj = Paths.get(expectedPath);

        // Compare path component count
        if (actualPathObj.getNameCount() != expectedPathObj.getNameCount()) {
            LOGGER.info("Path component count mismatch. Actual: " + actualPathObj.getNameCount() +
                    ", Expected: " + expectedPathObj.getNameCount()); //NO I18N
            return false;
        }

        // Compare each path component with proper case sensitivity based on OS
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win"); //NO I18N

        // If we're dealing with absolute paths on Windows with different drive letters,
        // the comparison should fail
        if (isWindows && actualPathObj.isAbsolute() && expectedPathObj.isAbsolute()) {
            String actualRoot = actualPathObj.getRoot().toString().toLowerCase();
            String expectedRoot = expectedPathObj.getRoot().toString().toLowerCase();
            if (!actualRoot.equals(expectedRoot)) {
                LOGGER.info("Drive letter mismatch. Actual: " + actualRoot +
                        ", Expected: " + expectedRoot); //NO I18N
                return false;
            }
        }

        // Compare each component
        for (int i = 0; i < actualPathObj.getNameCount(); i++) {
            String actualComponent = actualPathObj.getName(i).toString();
            String expectedComponent = expectedPathObj.getName(i).toString();

            boolean componentsMatch;
            if (isWindows) {
                // Case-insensitive comparison on Windows
                componentsMatch = actualComponent.equalsIgnoreCase(expectedComponent);
            } else {
                // Case-sensitive comparison on Unix/Linux
                componentsMatch = actualComponent.equals(expectedComponent);
            }

            if (!componentsMatch) {
                LOGGER.info("Path component mismatch at position " + i + ". Actual: " + actualComponent +
                        ", Expected: " + expectedComponent); //NO I18N
                return false;
            }
        }

        // All components match
        return true;
    }

    /**
     * Normalize a path for consistent comparison
     */
    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }

        // Convert all slashes to system separators
        String normalized = path.replace('/', File.separatorChar)
                .replace('\\', File.separatorChar);

        // Remove trailing separator if present
        if (normalized.endsWith(File.separator)) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        // Remove leading and trailing whitespace
        normalized = normalized.trim();

        return normalized;
    }

    /**
     * Kill a process
     *
     * @param processName The name of the process to kill
     * @param processPath The path to limit which processes to kill (optional)
     * @param op The operation object to store results
     * @return true if the process was successfully killed (or was not running), false otherwise
     */
    private static boolean killProcess(String processName, String processPath, Operation op) {
        LOGGER.info("Killing process: " + processName + (processPath != null ? " at path: " + processPath : "")); //No I18N

        StringBuilder remarkBuilder = new StringBuilder();
        remarkBuilder.append("Kill process operation:\n"); //No I18N
        remarkBuilder.append("- Process name: ").append(processName != null ? processName : "Not provided").append("\n"); //No I18N
        remarkBuilder.append("- Process path: ").append(processPath != null ? processPath : "Not provided").append("\n\n"); //No I18N

        // Check if we have sufficient information to proceed
        if (processName == null && (processPath == null || processPath.isEmpty())) {
            String message = "Either process name or process path must be provided"; //No I18N
            LOGGER.warning(message);
            remarkBuilder.append("FAILED: ").append(message).append("\n");
            op.setRemarks(remarkBuilder.toString());
            return false;
        }

        // Extract process name from path if only path is provided
        if ((processName == null || processName.isEmpty()) && processPath != null && !processPath.isEmpty()) {
            // Extract process name from the path
            File pathFile = new File(processPath);
            String extractedName = pathFile.getName();
            if (!extractedName.isEmpty()) {
                processName = extractedName;
                LOGGER.info("Extracted process name from path: " + processName); //No I18N
                remarkBuilder.append("Extracted process name from path: ").append(processName).append("\n"); //No I18N
            }
        }

        // Resolve server_home if present and construct expected path
        String resolvedPath = null;
        if (processPath != null && !processPath.isEmpty()) {
            // Check if the path already includes the process name
            boolean pathIncludesProcessName = false;
            if (processName != null && !processName.isEmpty()) {
                String normalizedPath = normalizePath(processPath);
                String normalizedProcessName = processName.toLowerCase();
                pathIncludesProcessName = normalizedPath.toLowerCase().endsWith(normalizedProcessName) ||
                        normalizedPath.toLowerCase().endsWith(normalizedProcessName + ".exe");
            }

            if (pathIncludesProcessName) {
                resolvedPath = processPath; // Use the path as-is
                LOGGER.info("Process path already includes process name: " + resolvedPath); //No I18N
                remarkBuilder.append("Process path already includes process name\n"); //No I18N
            } else {
                // Construct the complete path by appending the process name
                resolvedPath = constructExpectedProcessPath(processName, processPath);
//                LOGGER.info("Constructed complete process path: " + resolvedPath); //No I18N
//                remarkBuilder.append("Constructed complete path: ").append(resolvedPath).append("\n"); //No I18N
            }

            // Replace server_home if present
            if (resolvedPath.contains("server_home")) {
                String originalPath = resolvedPath;
                resolvedPath = ServerUtils.resolvePath(resolvedPath);
                remarkBuilder.append("Resolved server_home in path: ").append(originalPath)
                        .append(" → ").append(resolvedPath).append("\n"); //No I18N
            }
        }

        // Get all instances of the specified process
        List<ProcessInfo> matchingProcesses = ProcessScanner.getProcessesByName(processName);

        if (matchingProcesses.isEmpty()) {
            String message = "Process " + processName + " is not running, no need to kill"; //No I18N
            LOGGER.info(message);
            remarkBuilder.append("PASSED: ").append(message).append("\n");

            if (op != null) {
                op.setRemarks(remarkBuilder.toString());
                op.setOutputValue(message);
            }

            // Already not running is considered success for kill operation
            return true;
        }

        LOGGER.info("Found " + matchingProcesses.size() + " process(es) with name: " + processName); //No I18N
        remarkBuilder.append("Found ").append(matchingProcesses.size())
                .append(" process(es) with name: ").append(processName).append("\n\n"); //No I18N

        // Filter processes by path if specified
        List<ProcessInfo> processesToKill = new ArrayList<>();

        for (ProcessInfo process : matchingProcesses) {
            // If path is not specified, kill all instances
            if (resolvedPath == null || resolvedPath.isEmpty()) {
                processesToKill.add(process);
                continue;
            }

            // If path is specified, only kill processes in that path
            String executablePath = process.getExecutablePath();
            if (compareProcessPaths(executablePath, processName, resolvedPath)) {
                LOGGER.info("Process path matches criteria: " + process.getProcessId() + ", " + executablePath); //No I18N
                processesToKill.add(process);
                remarkBuilder.append("Process PID ").append(process.getProcessId())
                        .append(": path matches criteria\n"); //No I18N
            } else if (executablePath != null) {
                // Log detailed information about why paths don't match
                LOGGER.info("Process path doesn't match criteria, skipping: " + process.getProcessId()); //No I18N
                remarkBuilder.append("Process PID ").append(process.getProcessId())
                        .append(": path doesn't match criteria\n")
                        .append("  Expected: ").append(resolvedPath.replace('\\', '/')).append("\n")
                        .append("  Actual: ").append(executablePath.replace('\\', '/')).append("\n"); //No I18N
            }
        }

        if (processesToKill.isEmpty()) {
            // More detailed error message for troubleshooting
            String message = "No processes match both name '" + processName + "' and path '" + processPath + "' criteria"; //No I18N
            LOGGER.warning(message);
            remarkBuilder.append("\nFAILED: ").append(message).append("\n"); //No I18N

            if (op != null) {
                op.setRemarks(remarkBuilder.toString());
                op.setOutputValue("No processes found matching both name and path criteria"); //No I18N
            }

            return false;
        }

        // Kill each matching process
        boolean success = true;
        remarkBuilder.append("\nKill operations:\n"); //No I18N

        for (ProcessInfo process : processesToKill) {
            boolean killed = killProcessById(process.getProcessId());
            remarkBuilder.append("- PID ").append(process.getProcessId()) //No I18N
                    .append(" (").append(process.getName()).append("): ") //No I18N
                    .append(killed ? "Successfully terminated" : "Failed to terminate").append("\n"); //No I18N

            if (!killed) {
                success = false;
            }
        }

        // Store result in operation
        if (op != null) {
            remarkBuilder.append("\n").append(success ? "PASSED: " : "FAILED: ")
                    .append(success ?
                            "Successfully killed all " + processesToKill.size() + " matching processes" :
                            "Failed to kill some or all matching processes")
                    .append("\n"); //No I18N

            op.setRemarks(remarkBuilder.toString());
            op.setOutputValue(success ?
                    "Successfully killed all matching processes" :  //No I18N
                    "Failed to kill some or all matching processes"); //No I18N
        }

        LOGGER.info("Kill process result: " + (success ? "PASSED" : "FAILED")); //No I18N
        return success;
    }

    /**
     * Kill a process by its process ID
     *
     * @param processId The ID of the process to kill
     * @return true if process was successfully killed, false otherwise
     */
    private static boolean killProcessById(String processId) {
        try {
            LOGGER.info("Attempting to kill process with PID: " + processId); //No I18N

            Process process = Runtime.getRuntime().exec("taskkill /F /PID " + processId); //No I18N
            int exitCode = process.waitFor();

            boolean success = (exitCode == 0);
            LOGGER.info("Kill process " + processId + " result: " + (success ? "Success" : "Failed")); //No I18N

            return success;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error killing process with PID: " + processId, e); //No I18N
            return false;
        }
    }

    /**
     * Adapter method for TestExecutor to execute task manager operations
     *
     * @param processName Name of the process to verify or kill
     * @param action The action to perform ("verify_process" or "kill_process")
     * @param expectation The expected state ("processRunning" or "processNotRunning")
     * @return true if the operation was successful, false otherwise
     */
    public static boolean executeTaskManagerOperation(String processName, String action, String expectation) {
        LOGGER.info("Executing task manager operation via adapter: " + action + " on " + processName); //No I18N

        // Create a map of parameters
        Map<String, String> parameters = new HashMap<>();
        parameters.put("action", action); //No I18N
        parameters.put("process_name", processName); //No I18N

        if (expectation != null && !expectation.isEmpty()) {
            parameters.put("expect", expectation); //No I18N
        }

        // Create an operation object
        Operation operation = new Operation("task_manager", parameters); //No I18N

        // Execute the operation
        return executeOperation(operation);
    }

    /**
     * List all running processes with their basic info
     */
    private static boolean listAllProcesses(Operation op) {
        try {
            LOGGER.info("Listing all running processes");

            // Get filter parameter if provided
            String filter = op.getParameter("filter"); // Optional name filter

            // Execute tasklist command
            String command = "tasklist /V /FO CSV";
            String output = runCommandAndGetOutput(command);

            // Parse output and build result
            List<Map<String, String>> processInfoList = parseTasklistOutput(output);

            StringBuilder result = new StringBuilder();
            result.append("Running processes (" + processInfoList.size() + " total):\n\n");
            result.append(String.format("%-6s %-25s %-12s %-20s %-10s\n",
                    "PID", "NAME", "MEMORY", "USER", "STATUS"));
            result.append("--------------------------------------------------------------\n");

            int filteredCount = 0;

            for (Map<String, String> processInfo : processInfoList) {
                String name = processInfo.get("ImageName");

                // Apply filter if specified
                if (filter != null && !filter.isEmpty() && !name.toLowerCase().contains(filter.toLowerCase())) {
                    continue;
                }

                filteredCount++;

                result.append(String.format("%-6s %-25s %-12s %-20s %-10s\n",
                        processInfo.get("PID"),
                        truncate(name, 25),
                        processInfo.get("MemUsage"),
                        truncate(processInfo.get("User Name"), 20),
                        processInfo.get("Status")));
            }

            if (filter != null && !filter.isEmpty()) {
                result.insert(0, "Filtered processes matching '" + filter + "': " + filteredCount + "\n\n");
            }

            op.setRemarks(result.toString());
            op.setOutputValue("Retrieved " + filteredCount + " processes");
            return true;
        }
        catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error listing processes", e);
            op.setRemarks("Error listing processes: " + e.getMessage());
            return false;
        }
    }

    /**
     * Helper method to run a command and get its output
     */
    private static String runCommandAndGetOutput(String command) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe","/C", command);
        processBuilder.redirectErrorStream(true); // Merge error stream with output
        Process process = null;
        LOGGER.info("Executing command: " + command); //No I18N
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            LOGGER.severe("Failed to execute command: " + command+ e);
        }
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LOGGER.info("Command output: " + line); //No I18N
                output.append(line).append("\n");
            }
        }

        return output.toString();
    }

    /**
     * Extract user information from tasklist command output
     */
    private static String extractUserFromTasklist(String tasklistOutput) {
        String[] lines = tasklistOutput.split("\n");

        if (lines.length < 2) {
            return null;
        }

        // Parse the CSV output - the user name is in the 7th column (index 6)
        String dataLine = lines[1];
        String[] values = parseCSVLine(dataLine);

        if (values.length >= 7) {
            return values[6]; // User Name column (7th column, index 6)
        }

        return null;
    }

    /**
     * Parse a CSV line properly handling quoted fields
     */
    private static String[] parseCSVLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();

        for (char c : line.toCharArray()) {
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(currentField.toString().replace("\"", ""));
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }

        result.add(currentField.toString().replace("\"", ""));
        return result.toArray(new String[0]);
    }

    /**
     * Format memory size in bytes to a human-readable format
     */
    private static String formatMemorySize(long bytes) {
        // Always convert to KB regardless of size
        double kb = bytes / 1024.0;
        LOGGER.info("Memory size in KB: " + kb); //No I18N
        return String.format("%.2f KB", kb);
    }

    /**
     * Parse tasklist command output
     */
    private static List<Map<String, String>> parseTasklistOutput(String output) {
        List<Map<String, String>> result = new ArrayList<>();
        String[] lines = output.split("\n");

        if (lines.length < 2) {
            return result;
        }

        // Parse header line
        String[] headers = parseCSVLine(lines[0]);

        // Parse data lines
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            String[] values = parseCSVLine(line);

            if (values.length >= headers.length) {
                Map<String, String> processInfo = new HashMap<>();

                for (int j = 0; j < headers.length; j++) {
                    processInfo.put(headers[j], values[j]);
                }

                result.add(processInfo);
            }
        }

        return result;
    }

    /**
     * Calculate CPU usage percentage from user and kernel times
     */
    private static double calculateCpuPercentage(long userTime, long kernelTime) {
        // This is a simplified calculation
        // In a real implementation, you'd need to track changes over time
        long totalTime = userTime + kernelTime;
        return (totalTime / 10000000.0); // Convert 100-nanosecond units to seconds and to percentage
    }

    /**
     * Truncate string to specified length
     */
    private static String truncate(String input, int maxLength) {
        if (input == null) return "";
        return input.length() <= maxLength ? input : input.substring(0, maxLength - 3) + "...";
    }


/**
 * Main method for testing TaskManagerHandler functionality
 * @param args command line arguments (not used)
 */
// public static void main(String[] args) {
//     try {
//         System.out.println("Testing TaskManagerHandler with nginx.exe");
//         System.out.println("=========================================");

//         // Setup server home property
//         ServerUtils.setupServerHomeProperty("test"); // No I18N
//         String serverHome = System.getProperty("server.home"); // No I18N
//         if (serverHome == null) {
//             serverHome = ServerUtils.getProductServerHome();
//         }
//         System.out.println("Server home: " + serverHome);

//         // Define test parameters
//         String processName = "postgres.exe"; // No I18N
//         String processPath = "server_home/pgsql/bin"; // No I18N

//         // Test 1: Verify if process is running
//         System.out.println("\n1. Verifying if nginx.exe is running...");
//         Map<String, String> verifyParams = new HashMap<>();
//         verifyParams.put("action", "verify_process"); // No I18N
//         verifyParams.put("process_name", processName); // No I18N
//         verifyParams.put("process_path", processPath); // No I18N
//         verifyParams.put("expect", "processRunning"); // No I18N

//         Operation verifyOp = new Operation("task_manager", verifyParams); // No I18N
//         boolean verifyResult = executeOperation(verifyOp);

//         System.out.println("Verify result: " + (verifyResult ? "PASSED" : "FAILED"));
//         System.out.println("Remarks:\n" + verifyOp.getRemarks());

//         // Test 2: Kill process if running
//         if (verifyResult) {
//             System.out.println("\n2. Killing nginx.exe process...");
//             Map<String, String> killParams = new HashMap<>();
//             killParams.put("action", "kill_process"); // No I18N
//             killParams.put("process_name", processName); // No I18N
//             killParams.put("process_path", processPath); // No I18N

//             Operation killOp = new Operation("task_manager", killParams); // No I18N
//             boolean killResult = executeOperation(killOp);

//             System.out.println("Kill result: " + (killResult ? "PASSED" : "FAILED"));
//             System.out.println("Remarks:\n" + killOp.getRemarks());

//             // Verify again to confirm process is killed
//             System.out.println("\n3. Verifying nginx.exe is no longer running...");
//             verifyParams.put("expect", "processNotRunning"); // No I18N
//             Operation verifyAgainOp = new Operation("task_manager", verifyParams); // No I18N
//             boolean verifyAgainResult = executeOperation(verifyAgainOp);

//             System.out.println("Verify after kill: " + (verifyAgainResult ? "PASSED" : "FAILED"));
//             System.out.println("Remarks:\n" + verifyAgainOp.getRemarks());
//         } else {
//             // If process wasn't running, test the kill functionality anyway
//             System.out.println("\n2. Process not running. Testing kill on non-existent process...");
//             Map<String, String> killParams = new HashMap<>();
//             killParams.put("action", "kill_process"); // No I18N
//             killParams.put("process_name", processName); // No I18N
//             killParams.put("process_path", processPath); // No I18N

//             Operation killOp = new Operation("task_manager", killParams); // No I18N
//             boolean killResult = executeOperation(killOp);

//             System.out.println("Kill result: " + (killResult ? "PASSED" : "FAILED"));
//             System.out.println("Remarks:\n" + killOp.getRemarks());
//         }

//         // Test 3: Test the adapter method
//         System.out.println("\n4. Testing adapter method:");
//         boolean adapterResult = executeTaskManagerOperation(processName, "verify_process", "processNotRunning");
//         System.out.println("Adapter method result: " + (adapterResult ? "PASSED" : "FAILED"));

//     } catch (Exception e) {
//         System.err.println("Error in test execution:");
//         e.printStackTrace();
//     }
// }
}