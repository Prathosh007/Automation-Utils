package com.me.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for scanning and retrieving process information
 */
public class ProcessScanner {
    private static final Logger LOGGER = LogManager.getLogger(ProcessScanner.class.getName(), LogManager.LOG_TYPE.FW);
    private List<ProcessInfo> processList;

    /**
     * Constructor initializes the process scanner
     */
    public ProcessScanner() {
        this.processList = new ArrayList<>();
        // Don't automatically refresh to allow more control over when system 
        // resources are used for scanning processes
    }

    /**
     * Refresh the list of running processes
     * This method is optimized to be faster by using more efficient commands
     *
     * @return true if refresh was successful, false otherwise
     */
    public boolean refresh() {
        LOGGER.info("Refreshing process list"); //NO I18N

        try {
            // Clear existing process list
            processList.clear();

            // Platform-specific process scanning using optimized commands
            String osName = System.getProperty("os.name").toLowerCase();

            if (osName.contains("win")) {
                return refreshWindowsProcesses();
            } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("mac")) {
                return refreshUnixProcesses();
            } else {
                LOGGER.warning("Unsupported operating system: " + osName); //NO I18N
                return false;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error refreshing process list", e); //NO I18N
            return false;
        }
    }

    /**
     * Refresh process list on Windows using tasklist command
     */
    private boolean refreshWindowsProcesses() {
        try {
            // Use tasklist command to get process information with image path
            ProcessBuilder pb = new ProcessBuilder(
                    "cmd", "/c", "tasklist", "/v", "/fo", "csv"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Process output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                // Skip header line
                reader.readLine();

                // Process each line
                while ((line = reader.readLine()) != null) {
                    try {
                        // Parse CSV line
                        String[] parts = parseCSVLine(line);
                        if (parts.length >= 2) {
                            String processName = parts[0].trim().replace("\"", "");
                            int pid = Integer.parseInt(parts[1].trim().replace("\"", ""));

                            // Create ProcessInfo object
                            ProcessInfo processInfo = new ProcessInfo(String.valueOf(pid), processName);

                            // Get process path using PowerShell - this is the key change to fix path resolution
                            String path = getProcessPathByPid(pid);
                            processInfo.setExecutablePath(path);

                            processList.add(processInfo);
                        }
                    } catch (Exception e) {
                        // Skip problematic line
                        LOGGER.log(Level.FINE, "Error parsing process line: " + line, e); //NO I18N
                    }
                }
            }

            // Log the number of processes found
            LOGGER.info("Found " + processList.size() + " processes"); //NO I18N

            // Log sample process paths for debugging
            int logLimit = Math.min(5, processList.size());
            for (int i = 0; i < logLimit; i++) {
                ProcessInfo processInfo = processList.get(i);
                LOGGER.info("Sample process: " + processInfo.getName() + ", PID: " + processInfo.getPid() +
                        ", Path: " + processInfo.getPath()); //NO I18N
            }

            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error refreshing Windows processes", e); //NO I18N
            return false;
        }
    }

    /**
     * Get process path by PID using PowerShell on Windows
     */
    private String getProcessPathByPid(int pid) {
        try {
            // Use PowerShell to get the executable path - this command provides the full path
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-Command",
                    "(Get-Process -Id " + pid + " -ErrorAction SilentlyContinue).Path"
            );
            pb.redirectErrorStream(true);
//            LOGGER.info("Executing getProcessPathByPid command : "+pb.command());
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
//                        LOGGER.info("Found process path for PID " + pid + ": " + line); //NO I18N
                        return line;
                    }
                }
            }

            // If we can't get the path, use an alternative method
            return getProcessPathByPidAlternative(pid);

        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error getting process path for PID " + pid, e); //NO I18N
            // Try alternative method
            return getProcessPathByPidAlternative(pid);
        }
    }

    /**
     * Alternative method to get process path by PID using query
     */
    private String getProcessPathByPidAlternative(int pid) {
        try {
            // Alternative approach using query process
            ProcessBuilder pb = new ProcessBuilder(
                    "cmd", "/c", "query process " + pid + " /fo list /v"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Look for fields that might contain the path
                    if (line.trim().startsWith("CommandLine:") ||
                            line.trim().startsWith("ExecutablePath:")) {
                        String pathInfo = line.substring(line.indexOf(":") + 1).trim();
                        if (!pathInfo.isEmpty()) {
                            // Extract the executable path from command line if needed
                            if (pathInfo.startsWith("\"")) {
                                return pathInfo.substring(1, pathInfo.indexOf("\"", 1));
                            }
                            return pathInfo;
                        }
                    }
                }
            }

            return ""; // Path not found
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error in alternative process path lookup for PID " + pid, e); //NO I18N
            return ""; // Return empty string on error
        }
    }

    /**
     * Refresh process list on Unix/Linux/Mac systems
     */
    private boolean refreshUnixProcesses() {
        try {
            // Use ps command to get process information
            ProcessBuilder pb = new ProcessBuilder(
                    "ps", "-eo", "pid,comm,args"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Process output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                // Skip header line
                reader.readLine();

                // Process each line
                while ((line = reader.readLine()) != null) {
                    try {
                        // Parse space-delimited line
                        String[] parts = line.trim().split("\\s+", 3);
                        if (parts.length >= 2) {
                            int pid = Integer.parseInt(parts[0].trim());
                            String processName = parts[1].trim();
                            String arguments = parts.length > 2 ? parts[2].trim() : "";

                            // Extract path from arguments if available
                            String path = extractPathFromArguments(processName, arguments);

                            // Create ProcessInfo object
                            ProcessInfo processInfo = new ProcessInfo(pid + "", processName);
                            processInfo.setExecutablePath(path);

                            processList.add(processInfo);
                        }
                    } catch (Exception e) {
                        // Skip problematic line
                        LOGGER.log(Level.FINE, "Error parsing process line: " + line, e); //NO I18N
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                LOGGER.warning("ps command returned non-zero exit code: " + exitCode); //NO I18N
            }

            LOGGER.info("Found " + processList.size() + " processes"); //NO I18N
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error refreshing Unix processes", e); //NO I18N
            return false;
        }
    }

    /**
     * Get the list of process information
     *
     * @return List of ProcessInfo objects
     */
    public List<ProcessInfo> getProcessList() {
        return processList;
    }

    /**
     * Helper method to parse CSV line properly handling quotes
     */
    private String[] parseCSVLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (char c : line.toCharArray()) {
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        // Add the last field
        result.add(current.toString());

        return result.toArray(new String[0]);
    }

    /**
     * Get process path by PID using PowerShell on Windows
     * This is now a lazy-load method to improve performance
     */
    public String getProcessPath(int pid) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-Command",
                    "(Get-Process -Id " + pid + " -ErrorAction SilentlyContinue).Path"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        LOGGER.log(Level.FINE, "Found process path for PID " + pid + ": " + line); //NO I18N
                        return line;
                    }
                }
            }

            return ""; // Path not found
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error getting process path for PID " + pid, e); //NO I18N
            return ""; // Return empty string on error
        }
    }

    /**
     * Extract process path from command line arguments
     */
    private String extractPathFromArguments(String processName, String arguments) {
        // Simple extraction - first argument is often the path
        if (arguments.startsWith("/") || arguments.startsWith("-")) {
            // Command with switches, process name is probably the path
            return processName;
        } else {
            // Try to extract path from arguments
            String[] parts = arguments.split("\\s+");
            if (parts.length > 0) {
                return parts[0];
            }
        }
        return processName; // Fall back to process name if path can't be extracted
    }

    /**
     * Get the full path of a process statically
     *
     * @param pid Process ID
     * @return Full path or empty string if not found
     */
    public static String getProcessPathStatic(int pid) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "powershell", "-Command",
                    "(Get-Process -Id " + pid + " -ErrorAction SilentlyContinue).Path"
            );
            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        LOGGER.fine("Found process path for PID " + pid + ": " + line);
                        return line;
                    }
                    output.append(line).append("\n");
                }
            }

            process.waitFor();

            // Log if path was not found for debugging
            LOGGER.warning("No path found for process with PID " + pid + ". PowerShell output: " + output.toString());

        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.WARNING, "Error getting process path for PID: " + pid, e);
        }

        return "";
    }

    /**
     * Process information class
     */
    public static class ProcessInfo {
        private String processId;
        private String executablePath;
        private String processName;
        private Set<Integer> ports = new HashSet<>(); // Add ports field to store which ports the process is using

        public ProcessInfo(String processId, String executablePath) {
            this.processId = processId;
            this.executablePath = executablePath;
        }

        public String getProcessId() {
            return processId;
        }

        public String getPid() {
            return processId;
        }
        public String getProcessName() {
            return processName;
        }
        public String getName() {
            // Extract name from the executable path
            if (executablePath != null && !executablePath.isEmpty()) {
                int lastSeparatorIndex = Math.max(
                        executablePath.lastIndexOf('/'),
                        executablePath.lastIndexOf('\\')
                );
                if (lastSeparatorIndex >= 0 && lastSeparatorIndex < executablePath.length() - 1) {
                    return executablePath.substring(lastSeparatorIndex + 1);
                }
            }
            return executablePath;
        }

        public String getExecutablePath() {
            return executablePath;
        }

        public void setExecutablePath(String executablePath) {
            this.executablePath = executablePath;
        }

        public String getPath() {
            return executablePath;
        }

        @Override
        public String toString() {
            return "PID: " + processId + ", Path: " + executablePath;//No I18N
        }

        public void setPorts(Set<Integer> ports) {
            this.ports = ports;
        }

        public Set<Integer> getPorts() {
            return ports;
        }

        public void addPort(int port) {
            this.ports.add(port);
        }

        public boolean isUsingPort(int port) {
            return this.ports.contains(port);
        }


    }

    /**
     * Get all instances of a process by name
     *
     * @param processName Process name to search for
     * @return List of ProcessInfo objects for matching processes
     */
    public static List<ProcessInfo> getProcessesByName(String processName) {
        List<ProcessInfo> processes = new ArrayList<>();

        try {
            String command = "powershell -Command \"Get-Process -Name '" + processName.replace(".exe", "") + "' -ErrorAction SilentlyContinue | Select-Object Id,Path | ForEach-Object { Write-Output \\\"ProcessId=$($_.Id)\\\"; Write-Output \\\"ExecutablePath=$($_.Path)\\\"; Write-Output \\\"\\\" }\"";//No I18N

            BufferedReader reader = getDataFromCMD(command);
            if (reader == null) {
                LOGGER.log(Level.WARNING, "Failed to get BufferedReader from command");//No I18N
                return processes;
            }

            String line;
            String currentPid = null;
            String currentPath = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty()) {
                    // Empty line indicates end of current process info
                    if (currentPid != null && currentPath != null) {
                        processes.add(new ProcessInfo(currentPid, currentPath));
                    }
                    currentPid = null;
                    currentPath = null;
                    continue;
                }

                // Parse ProcessId and ExecutablePath lines
                if (line.startsWith("ProcessId=")) {
                    currentPid = line.substring("ProcessId=".length()).trim();
                } else if (line.startsWith("ExecutablePath=")) {
                    currentPath = line.substring("ExecutablePath=".length()).trim();
                }
            }

            // Add last process if not added yet
            if (currentPid != null && currentPath != null) {
                processes.add(new ProcessInfo(currentPid, currentPath));
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting processes", e);//No I18N
        }

        return processes;
    }

    public static BufferedReader getDataFromCMD(String command){
        BufferedReader errorStream = null;
        BufferedReader reader = null;

        try{
            String[] commands= {"cmd.exe","/C",command};
            LOGGER.log(Level.SEVERE,"executing PowerShell command ==> "+ Arrays.toString(commands));
            ProcessBuilder processBuilder= new ProcessBuilder(commands);
            Process process = processBuilder.start();
            //getting inputStream from the process and returning;
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            errorStream = new BufferedReader(new InputStreamReader(process.getErrorStream()));
//        logger.info("error stream occurred"+errorStream.readLine());
            return reader;
        }catch (IOException e){
            LOGGER.log(Level.SEVERE,"Error getting data from PowerShell",e);
            return reader;
        }
    }

    public static BufferedReader runCMDCommand(String command){
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();

            // Extract directory and command parts
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

            Process process = processBuilder.start();

            // Start error reading thread
            new Thread(() -> {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        LOGGER.log(Level.WARNING, "CMD Error: " + line);
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Error reading error stream", e);
                }
            }).start();

            return new BufferedReader(new InputStreamReader(process.getInputStream()));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error executing CMD command", e);
            return null;
        }
    }

    /**
     * Group processes by executable path
     *
     * @param processName Process name to search for
     * @return Map with paths as keys and lists of process IDs as values
     */
    public static Map<String, List<String>> getProcessesGroupedByPath(String processName) {
        Map<String, List<String>> groupedProcesses = new HashMap<>();

        for (ProcessInfo process : getProcessesByName(processName)) {
            String path = process.getExecutablePath();
            if (!groupedProcesses.containsKey(path)) {
                groupedProcesses.put(path, new ArrayList<>());
            }
            groupedProcesses.get(path).add(process.getProcessId());
        }

        return groupedProcesses;
    }

    /**
     * Print information about processes grouped by path
     *
     * @param processName Process name to search for
     */
    public static void printProcessesByPath(String processName) {
        Map<String, List<String>> groupedProcesses = getProcessesGroupedByPath(processName);

        if (groupedProcesses.isEmpty()) {
            LOGGER.log(Level.INFO, "No instances of " + processName + " found.");//No I18N
            return;
        }

        LOGGER.log(Level.INFO,
                "Found " + getTotalProcessCount(groupedProcesses) + //No I18N
                        " instance(s) of " + processName + " in " + //No I18N
                        groupedProcesses.size() + " different path(s):");//No I18N

        Logger logger = LogManager.getLogger(ProcessScanner.class.getName(), LogManager.LOG_TYPE.FW);
        for (Map.Entry<String, List<String>> entry : groupedProcesses.entrySet()) {
            logger.log(Level.INFO, "Path: {0}", entry.getKey());//No I18N
            logger.log(Level.INFO, "Process count: {0}", entry.getValue().size());//No I18N
            logger.log(Level.INFO, "Process IDs: {0}", String.join(", ", entry.getValue()));//No I18N
        }
    }

    /**
     * Get the total number of processes across all paths
     */
    private static int getTotalProcessCount(Map<String, List<String>> groupedProcesses) {
        int count = 0;
        for (List<String> pids : groupedProcesses.values()) {
            count += pids.size();
        }
        return count;
    }

    /**
     * Get information about running processes matching a process name
     *
     * @param processName Name of the process to look for
     * @return List of maps containing process details
     */
//    public static List<Map<String, String>> getProcesses(String processName) {
//        List<Map<String, String>> result = new ArrayList<>();
//
//        try {
//            ProcessBuilder builder;
//            if (System.getProperty("os.name").toLowerCase().contains("win")) {//No I18N
//                // Windows command to get process information
//                builder = new ProcessBuilder(
//                        "powershell", "-Command",
//                        "Get-Process -Name '" + processName.replace(".exe", "") + "' -ErrorAction SilentlyContinue | Select-Object Id,Path | ForEach-Object { Write-Output \"ProcessId=$($_.Id)\"; Write-Output \"ExecutablePath=$($_.Path)\"; Write-Output \"\" }"
//                );
//            } else {
//                // Unix/Linux command to get process information
//                builder = new ProcessBuilder(
//                    "sh", "-c", "ps -ef | grep " + processName + " | grep -v grep"//No I18N
//                );
//            }
//
//            Process process = builder.start();
//            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
//
//            String line;
//            Map<String, String> currentProcess = null;
//
//            while ((line = reader.readLine()) != null) {
//                line = line.trim();
//
//                // On Windows, PowerShell output format:
//                // ExecutablePath=C:\path\to\process.exe
//                // ProcessId=1234
//                // [blank line]
//                if (line.isEmpty()) {
//                    if (currentProcess != null && !currentProcess.isEmpty()) {
//                        result.add(currentProcess);
//                    }
//                    currentProcess = new HashMap<>();
//                    continue;
//                }
//
//                if (System.getProperty("os.name").toLowerCase().contains("win")) {//No I18N
//                    // Parse Windows output
//                    int equalPos = line.indexOf('=');
//                    if (equalPos > 0) {
//                        String key = line.substring(0, equalPos).trim();
//                        String value = line.substring(equalPos + 1).trim();
//
//                        if (key.equals("ExecutablePath")) {//No I18N
//                            currentProcess.put("Path", value);//No I18N
//                        } else if (key.equals("ProcessId")) {//No I18N
//                            currentProcess.put("PID", value);//No I18N
//                        }
//                    }
//                } else {
//                    // Parse Unix/Linux output (simple approach)
//                    // Format: UID PID PPID C STIME TTY TIME CMD
//                    String[] parts = line.split("\\s+");
//                    if (parts.length >= 8) {
//                        currentProcess = new HashMap<>();
//                        currentProcess.put("PID", parts[1]);
//
//                        // Get full command path
//                        StringBuilder cmd = new StringBuilder();
//                        for (int i = 7; i < parts.length; i++) {
//                            if (i > 7) {cmd.append(" ");}
//                            cmd.append(parts[i]);
//                        }
//                        currentProcess.put("Path", cmd.toString());
//
//                        result.add(currentProcess);
//                    }
//                }
//            }
//
//            // Add last process if not added yet
//            if (currentProcess != null && !currentProcess.isEmpty()) {
//                result.add(currentProcess);
//            }
//
//            process.waitFor();
//        } catch (Exception e) {
//            LOGGER.log(Level.SEVERE, "Error retrieving process information", e);
//        }
//
//        return result;
//    }

    /**
     * Scan for process-port mappings using netstat command
     * Updates the port information in processInfoList
     */
    public void scanPortUsage() {
        try {
            // Create a map of PIDs to ProcessInfo objects for quick lookup
            Map<String, ProcessInfo> pidToProcessMap = new HashMap<>();
            for (ProcessInfo process : getProcessesByName("")) {
                pidToProcessMap.put(process.getProcessId(), process);
            }

            // Run netstat command to get port information
            ProcessBuilder pb = new ProcessBuilder("netstat", "-ano");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Parse netstat output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                // Skip header lines
                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith("Proto")) {
                        break;
                    }
                }

                // Process connection lines
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }

                    try {
                        // Parse netstat line - format varies by OS
                        // Windows format example: TCP    127.0.0.1:8383    0.0.0.0:0    LISTENING    1234
                        String[] parts = line.split("\\s+");
                        if (parts.length < 5) {
                            continue;
                        }

                        // Extract port and PID information
                        String localAddress = parts[1];
                        String pid = parts[parts.length - 1];

                        // Extract port number from local address
                        int portIndex = localAddress.lastIndexOf(':');
                        if (portIndex > 0) {
                            int port = Integer.parseInt(localAddress.substring(portIndex + 1));

                            // Associate port with process
                            ProcessInfo processInfo = pidToProcessMap.get(pid);
                            if (processInfo != null) {
                                processInfo.addPort(port);
                            }
                        }
                    } catch (Exception e) {
                        // Skip lines that can't be parsed
                        LOGGER.log(Level.FINE, "Error parsing netstat line: " + line, e); //NO I18N
                    }
                }
            }

            // Wait for the process to exit
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                LOGGER.warning("Netstat command returned non-zero exit code: " + exitCode); //NO I18N
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error scanning port usage", e); //NO I18N
        }
    }

    /**
     * Find a process by port number
     *
     * @param port The port number to look for
     * @return The ProcessInfo object for the process using the port, or null if not found
     */
    public ProcessInfo findProcessByPort(int port) {
        try {
            LOGGER.info("Looking for process using port: " + port); //NO I18N

            // On Windows, use netstat command to find which process is using a specific port
            ProcessBuilder pb = new ProcessBuilder(
                    "cmd", "/c", "netstat -ano | findstr :" + port
            );
            pb.redirectErrorStream(true);
            LOGGER.info("Executing command : "+pb.command());
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            String pidStr = null;

            // Read output and look for the process ID
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");

                    // Parse the netstat output to extract the PID
                    // Format: Protocol  Local Address  Foreign Address  State  PID
                    String trimmed = line.trim();
                    if (trimmed.contains(":" + port) &&
                            (trimmed.contains("LISTENING") || trimmed.contains("ESTABLISHED"))) {

                        // The PID should be the last column
                        String[] parts = trimmed.split("\\s+");
                        if (parts.length > 0) {
                            pidStr = parts[parts.length - 1];
                            LOGGER.info("Found process with PID " + pidStr + " using port " + port); //NO I18N
                            break;
                        }
                    }
                }
            }

            // If we found a PID, get the process details
            if (pidStr != null) {
                try {
                    int pid = Integer.parseInt(pidStr);

                    // Get process name and path for this PID
                    ProcessInfo processInfo = getProcessInfoByPid(pid);
                    if (processInfo != null) {
                        // Add this port to the process's ports set
                        Set<Integer> ports = processInfo.getPorts();
                        if (ports == null) {
                            ports = new HashSet<>();
                            processInfo.setPorts(ports);
                        }
                        ports.add(port);

                        return processInfo;
                    }
                } catch (NumberFormatException e) {
                    LOGGER.log(Level.WARNING, "Error parsing PID: " + pidStr, e); //NO I18N
                }
            }

            LOGGER.info("No process found using port " + port); //NO I18N
            return null;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error finding process by port", e); //NO I18N
            return null;
        }
    }

    /**
     * Get process information by PID without refreshing all processes
     *
     * @param pid Process ID
     * @return ProcessInfo for the specified PID, or null if not found
     */
    public ProcessInfo getProcessInfoByPid(int pid) {
        try {
            ProcessInfo info = new ProcessInfo(String.valueOf(pid), "");

            // Get process name using tasklist
            ProcessBuilder pb = new ProcessBuilder(
                    "tasklist", "/FI", "PID eq " + pid, "/FO", "CSV", "/NH"
            );
            pb.redirectErrorStream(true);
            LOGGER.info("Executing getProcessInfoByPid command : "+pb.command());
            Process process = pb.start();

            // Read the process name
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    // Parse CSV line to get process name
                    String[] parts = parseCSVLine(line);
                    if (parts.length > 0) {
                        String processName = parts[0].replace("\"", "");
                        LOGGER.info("Found process name for PID " + pid + ": " + processName); //NO I18N
                        info.processName = processName;
                    }
                }
            }

            // Get process path using PowerShell
            String path = getProcessPathByPid(pid);
            info.executablePath = path;

            return info;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting process info by PID: " + pid, e); //NO I18N
            return null;
        }
    }

    /**
     * Find processes by name without doing a full refresh
     *
     * @param processName The name of the process to find
     * @param processPath Optional path to filter results
     * @return List of matching ProcessInfo objects
     */
    public List<ProcessInfo> findProcessesByName(String processName, String processPath) {
        List<ProcessInfo> matchingProcesses = new ArrayList<>();

        try {
            LOGGER.info("Looking for processes with name: " + processName); //NO I18N
            if (processPath != null && !processPath.isEmpty()) {
                LOGGER.info("  with path: " + processPath); //NO I18N
            }

            // Use tasklist to find processes by name (case-insensitive on Windows)
            ProcessBuilder pb;

            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                // Windows
                pb = new ProcessBuilder(
                        "cmd", "/c", "tasklist", "/FI", "IMAGENAME eq " + processName, "/FO", "CSV", "/NH"
                );
            } else {
                // Unix/Linux/Mac
                pb = new ProcessBuilder(
                        "sh", "-c", "ps -eo pid,comm,args | grep -i " + processName
                );
            }

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Process output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    try {
                        ProcessInfo info;

                        if (System.getProperty("os.name").toLowerCase().contains("win")) {
                            // Parse CSV output from tasklist
                            String[] parts = parseCSVLine(line);
                            if (parts.length >= 2) {
                                String name = parts[0].replace("\"", "");
                                int pid = Integer.parseInt(parts[1].replace("\"", ""));

                                // Only create process info if name matches (case-insensitive)
                                if (name.equalsIgnoreCase(processName)) {
                                    info = new ProcessInfo(String.valueOf(pid), "");
                                    info.processName = name;

                                    // Get process path only if we need it
                                    if (processPath != null && !processPath.isEmpty()) {
                                        String path = getProcessPathByPid(pid);
                                        info.setExecutablePath(path);
                                    }

                                    matchingProcesses.add(info);
                                }
                            }
                        } else {
                            // Parse output from ps on Unix
                            if (line.toLowerCase().contains(processName.toLowerCase())) {
                                String[] parts = line.trim().split("\\s+", 3);
                                if (parts.length >= 2) {
                                    int pid = Integer.parseInt(parts[0]);
                                    String name = parts[1];

                                    info = new ProcessInfo(String.valueOf(pid), "");
                                    info.processName = name;

                                    // Extract path if needed
                                    if (processPath != null && !processPath.isEmpty() && parts.length > 2) {
                                        String path = extractPathFromArguments(parts[1], parts[2]);
                                        info.setExecutablePath(path);
                                    }

                                    matchingProcesses.add(info);
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Skip problematic line
                        LOGGER.log(Level.FINE, "Error parsing process line: " + line, e); //NO I18N
                    }
                }
            }

            LOGGER.info("Found " + matchingProcesses.size() + " matching processes"); //NO I18N

            // Log some details of the found processes
            for (int i = 0; i < Math.min(5, matchingProcesses.size()); i++) {
                ProcessInfo pi = matchingProcesses.get(i);
                LOGGER.info("  [" + i + "] PID: " + pi.getPid() + ", Path: " + pi.getExecutablePath()); //NO I18N
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error finding processes by name", e); //NO I18N
        }

        return matchingProcesses;
    }
}
