package com.me;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.File;

import com.me.util.LogManager;
import com.adventnet.mfw.ConsoleOut;

/**
 * Main menu for the G.O.A.T Framework
 */
public class GOATMenu {
    private static final Logger LOGGER = LogManager.getLogger(GOATMenu.class, LogManager.LOG_TYPE.FW);
    
    /**
     * Main method to run the G.O.A.T menu
     */
    public static void main(String[] args) {
        try {
            showMenu();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in GOAT menu", e);
            ConsoleOut.println("Error: " + e.getMessage()); //NO I18N
        }
    }
    
    /**
     * Display the menu and handle user choices
     */
    private static void showMenu() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        boolean running = true;
        
        while (running) {
            // Clear screen (doesn't work perfectly in all consoles)
            ConsoleOut.print("\033[H\033[2J"); //NO I18N
            System.out.flush();
            
            ConsoleOut.println("\n===================================================="); //NO I18N
            ConsoleOut.println("                 G.O.A.T Framework                   "); //NO I18N
            ConsoleOut.println("     (Generic Orchestrated Automated Testing)        "); //NO I18N
            ConsoleOut.println("====================================================\n"); //NO I18N
            ConsoleOut.println("Select an operation:\n"); //NO I18N
            ConsoleOut.println(" 1. Convert test cases (Natural language to JSON)"); //NO I18N
            ConsoleOut.println(" 2. Execute test cases (from JSON file)"); //NO I18N
            ConsoleOut.println(" 3. Run process and service tests"); //NO I18N
            ConsoleOut.println(" 4. Run file operations tests"); //NO I18N
            ConsoleOut.println(" 5. Fix JSON file paths"); //NO I18N
            ConsoleOut.println(" 6. View HTML test report"); //NO I18N
            ConsoleOut.println(" 7. Build framework"); //NO I18N
            ConsoleOut.println(" 8. Rerun failed tests"); //NO I18N
            ConsoleOut.println(" 9. Test API connection"); //NO I18N
            ConsoleOut.println("10. Setup Ollama model"); //NO I18N
            ConsoleOut.println(" 0. Exit"); //NO I18N
            ConsoleOut.println("\n====================================================\n"); //NO I18N
            
            ConsoleOut.print("Enter your choice (0-10): "); //NO I18N
            String choiceStr = reader.readLine().trim();
            int choice;
            
            try {
                choice = Integer.parseInt(choiceStr);
            } catch (NumberFormatException e) {
                ConsoleOut.println("Invalid choice. Please enter a number."); //NO I18N
                waitForKeyPress();
                continue;
            }
            
            switch (choice) {
                case 0:
                    running = false;
                    ConsoleOut.println("\nThank you for using G.O.A.T Framework!"); //NO I18N
                    break;
                    
                case 1:
                    ConsoleOut.println("\nRunning test case conversion..."); //NO I18N
                    runCommand("convert_testcases.bat");//NO I18N
                    break;
                    
                case 2:
                    ConsoleOut.println("\nRunning test case execution..."); //NO I18N
                    runCommand("run_tests_only.bat");//NO I18N
                    break;
                    
                case 3:
                    ConsoleOut.println("\nRunning process and service tests..."); //NO I18N
                    runCommand("run_process_service_tests.bat");//NO I18N
                    break;
                    
                case 4:
                    ConsoleOut.println("\nRunning file operations tests..."); //NO I18N
                    runCommand("run_file_operations_test.bat");//NO I18N
                    break;
                    
                case 5:
                    ConsoleOut.println("\nFix JSON file paths..."); //NO I18N
                    // Ask for the JSON file path
                    ConsoleOut.print("Enter JSON file path (e.g., manual_cases/file_operations_test.json): "); //NO I18N
                    String jsonPath = reader.readLine().trim();
                    if (!jsonPath.isEmpty()) {
                        runCommand("fix_json_paths.bat \"" + jsonPath + "\"");//NO I18N
                    } else {
                        ConsoleOut.println("No file path provided."); //NO I18N
                    }
                    break;
                    
                case 6:
                    ConsoleOut.println("\nViewing HTML test report..."); //NO I18N
                    runCommand("show_report.bat");//NO I18N
                    break;
                    
                case 7:
                    ConsoleOut.println("\nBuilding framework..."); //NO I18N
                    runCommand("build.bat");//NO I18N
                    break;
                    
                case 8:
                    ConsoleOut.println("\nRerunning failed tests..."); //NO I18N
                    runCommand("rerun_failed_tests.bat");//NO I18N
                    break;
                    
                case 9:
                    ConsoleOut.println("\nTesting API connection..."); //NO I18N
                    runCommand("test_api_connection.bat");//NO I18N
                    break;
                    
                case 10:
                    ConsoleOut.println("\nSetting up Ollama model..."); //NO I18N
                    runCommand("setup_ollama_model.bat");//NO I18N
                    break;
                    
                default:
                    ConsoleOut.println("\nInvalid choice. Please try again."); //NO I18N
                    break;
            }
            
            if (running) {
                waitForKeyPress();
            }
        }
    }
    
    /**
     * Run a command using the system's command processor
     */
    private static void runCommand(String command) {
        try {
            // Get the current working directory for diagnostic purposes
            String currentDir = System.getProperty("user.dir");
            LOGGER.info("Current working directory: " + currentDir);
            
            // Determine the absolute path to the bin directory
            File binDir = new File(currentDir);
            // If we're not already in the bin directory, try to find it
            if (!binDir.getName().equals("bin")) {
                // Check if we're in the product_package directory
                if (binDir.getName().equals("product_package")) {
                    binDir = new File(binDir, "bin");
                } 
                // Check if product_package/bin exists as a subdirectory
                else {
                    File possibleBinDir = new File(binDir, "product_package/bin");
                    if (possibleBinDir.exists() && possibleBinDir.isDirectory()) {
                        binDir = possibleBinDir;
                    }
                }
            }
            
            String binPath = binDir.getAbsolutePath();
            LOGGER.info("Using bin directory: " + binPath);
            
            // Check if command file exists before trying to run it
            File cmdFile = new File(binDir, command);
            if (!cmdFile.exists()) {
                ConsoleOut.println("ERROR: Command file not found: " + cmdFile.getAbsolutePath()); //NO I18N
                LOGGER.severe("Command file not found: " + cmdFile.getAbsolutePath());//NO I18N
                return;
            }
            
            ConsoleOut.println("Executing: " + cmdFile.getAbsolutePath()); //NO I18N
            
            // Use ProcessBuilder with inheritIO to handle interactive processes
            ProcessBuilder pb;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", "cd", "/d", binPath, "&&", command);//NO I18N
            } else {
                pb = new ProcessBuilder("sh", "-c", "cd \"" + binPath + "\" && ./" + command);//NO I18N
            }
            
            // This is the key change - inherit IO streams to allow user input
            pb.inheritIO();
            
            // Start the process and wait for it to complete
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                ConsoleOut.println("Command returned non-zero exit code: " + exitCode); //NO I18N
                LOGGER.warning("Command returned non-zero exit code: " + exitCode);
            }
            
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Error running command: " + command, e);
            ConsoleOut.println("Error executing command: " + e.getMessage()); //NO I18N
        }
    }
    
    /**
     * Wait for a key press
     */
    private static void waitForKeyPress() throws IOException {
        ConsoleOut.println("\nPress Enter to continue..."); //NO I18N
        new BufferedReader(new InputStreamReader(System.in)).readLine();
    }
}
