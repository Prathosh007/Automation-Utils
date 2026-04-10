package com.me.testcases;

import com.me.Operation;
import com.me.ResolveOperationParameters;
import com.me.util.LogManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.logging.Logger;

import static com.me.util.GOATCommonConstants.AUTOIT_EXE;

public class UnInstallProduct {
    private static final Logger LOGGER = LogManager.getLogger(UnInstallProduct.class, LogManager.LOG_TYPE.FW);
    /**
     * Execute an uninstall operation
     *
     * @param op The operation to execute
     * @return true if successful, false if failed
     */
    public static boolean executeOperation(Operation op) {
        StringBuilder remarksBuilder = new StringBuilder();
        if (op == null) {
            LOGGER.warning("Operation is null");
            return false;
        }

        op = ResolveOperationParameters.resolveOperationParameters(op);

        String autoItPath = op.getParameter("autoit_path");
        String args = op.getParameter("args");
        try {
            String[] scriptArgs = args.split(",");
            for (int i = 0; i < scriptArgs.length; i++) {
                scriptArgs[i] = scriptArgs[i].trim();
            }
            String[] command = new String[scriptArgs.length + 1];
            command[0] = autoItPath;
            System.arraycopy(scriptArgs, 0, command, 1, scriptArgs.length);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(autoItPath+AUTOIT_EXE));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    LOGGER.info("AutoIT Output: " + line);
                }
            }
            int exitCode = process.waitFor();
            LOGGER.info("AutoIT process exited with code: " + exitCode);
            remarksBuilder.append("AutoIt_Script_run_with_exit_code : ").append(exitCode).append("\n");
            if (exitCode == 0) {
                op.setRemarks(remarksBuilder.toString());
                return true;
            } else {
                remarksBuilder.append("AutoIt_Script_Output: ").append(output.toString()).append("\n");
                op.setRemarks(remarksBuilder.toString());
//                String logFilePath = getToolServerHome() + "\\Logs\\AutoIT.log";
//                if (new File(logFilePath).exists()) {
//                    remarksBuilder.append("Refer log file for more details: ").append(logFilePath);
//                }
                return false;
            }
        } catch (Exception e) {
            LOGGER.severe("Error executing AutoIT script: " + e.getMessage());
            remarksBuilder.append("Error executing AutoIT script: ").append(e.getMessage());
            op.setRemarks(remarksBuilder.toString());
            return false;
        }


    }
}
