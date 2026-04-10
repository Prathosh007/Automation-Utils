package com.me.util;

import com.me.Operation;

import java.io.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.me.testcases.ServerUtils.getToolServerHome;
import static com.me.util.GOATCommonConstants.EXEINSTALLARGSBAT;


public class CommandProcessor {
    private static StringUtil stringUtil = null;
    private final static Logger LOGGER = LogManager.getLogger(CommandProcessor.class.getName(), LogManager.LOG_TYPE.FW);
    protected String cmd;
    private File dir = null;
    private String result;
    private String error;
    private int exitValue;
    private Process process;
    private Consumer<CommandProcessor> print = p -> LOGGER.info(String.format("\nResult:\n%s\nError:\n%s\nExit value: %d", result, error, exitValue).replaceAll("\n+", "\n"));
    private Function<String, String> hide = str -> str;
    private Function<CommandProcessor, CommandProcessor> readResult = p -> p.setResult(getStringUtil().inputStreamToString(p.process.getInputStream())).setError(getStringUtil().inputStreamToString(p.process.getErrorStream())).setExitValue(p.process.exitValue());
    protected Function<CommandProcessor, CommandProcessor> function = p -> {
        if (cmd != null) {
            try {
                process = Runtime.getRuntime().exec(cmd, null, dir);
            } catch (IOException e) {
                LOGGER.warning(e::toString);
            }
        }

        if (readResult != null) {
            readResult.apply(p);
        }

        print();
        return this;
    };

    public CommandProcessor run() {
        LOGGER.info(getClass().getSimpleName() + ": " + hide.apply(cmd));
        return function.apply(this);
    }

    public void print() {
        print.accept(this);
    }

    public CommandProcessor setDir(String dir) {
        this.dir = new File(dir);
        return this;
    }

    public CommandProcessor setReadResult(Function<CommandProcessor, CommandProcessor> readResult) {
        this.readResult = readResult;
        return this;
    }

    public CommandProcessor setPrint(Consumer<CommandProcessor> print) {
        this.print = print;
        return this;
    }

    public String getResult() {
        return result;
    }

    public CommandProcessor setResult(String result) {
        this.result = hide.apply(result);
        return this;
    }

    public String getError() {
        return error;
    }

    public CommandProcessor setError(String error) {
        this.error = hide.apply(error);
        return this;
    }

    public int getExitValue() {
        return exitValue;
    }

    public CommandProcessor setExitValue(int exitValue) {
        this.exitValue = exitValue;
        return this;
    }

    public String getCmd() {
        return cmd;
    }

    public CommandProcessor setCmd(String cmd) {
        this.cmd = cmd;
        return this;
    }

    public Function<String, String> getHide() {
        return hide;
    }

    public CommandProcessor setHide(Function<String, String> hide) {
        this.hide = hide;
        return this;
    }

    public Function<CommandProcessor, CommandProcessor> getFunction() {
        return function;
    }

    public CommandProcessor setFunction(Function<CommandProcessor, CommandProcessor> function) {
        this.function = function;
        return this;
    }

    public Process getProcess() {
        return process;
    }

    public static StringUtil getStringUtil() {
        if (stringUtil == null) {
            stringUtil = new StringUtil();
        }
        return stringUtil;
    }

    public static boolean addSchedulerTask(String taskName, String fileLoc, String arguments, String batFilePath, Operation operation) {
        try {
            File file = new File(getToolServerHome() + EXEINSTALLARGSBAT);
            if (file.exists()) {
                file.delete();
            }
            file.createNewFile();
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file));
            bufferedWriter.write("@echo off\n " + fileLoc + " " + arguments);
            bufferedWriter.flush();
            bufferedWriter.close();
            CommandProcessor commandProcessor = new CommandProcessor();
            LOGGER.log(Level.INFO,"Command to add scheduler task: " + "schtasks /create /tn \"" + taskName + "\" /tr "+ "\"" + file.getAbsolutePath() + "\" /sc once /st 15:30 /sd 05/06/2090");
            operation.setRemarks("Going to add scheduler task: " + "schtasks /create /tn \"" + taskName + "\" /tr "+ "\"" + file.getAbsolutePath() + "\" /sc once /st 15:30 /sd 05/06/2090");
            commandProcessor.setCmd("schtasks /create /tn \"" + taskName + "\" /tr "+ "\"" + file.getAbsolutePath() + "\" /sc once /st 15:30 /sd 05/06/2090");
            LOGGER.log(Level.INFO,"Going to add scheduler task.....");
            commandProcessor.run();
            LOGGER.log(Level.INFO,"Waiting for scheduler task to complete...");
            LOGGER.log(Level.INFO,"Scheduler task added with exit code of : " + commandProcessor.getExitValue());
            return commandProcessor.getExitValue() == 0;
        }catch (Exception e){
            operation.setRemarks("Error while adding scheduler task: " + e.getMessage());
            LOGGER.log(Level.SEVERE,"Error while adding scheduler task: " + e.getMessage());
            return false;
        }
    }

    public static boolean runSchedulerTask(String taskName, String processName) {
        try {
            // Step 1: Trigger the scheduled task
            String triggerCommand = "schtasks /run /tn " + taskName;
            Process triggerTask = Runtime.getRuntime().exec(triggerCommand);
            triggerTask.waitFor(); // Optional, wait until the task is triggered
            LOGGER.log(Level.INFO, "Task triggered. Waiting for process to complete...");

            // Step 2: Monitor the process
            boolean isRunning = true;
            long timeout = 600000; // 10 minutes in milliseconds
            long checkInterval = 2000;

            while (isRunning && timeout > 0) {
                // Run tasklist to check for the process
                Process checkProcess = Runtime.getRuntime().exec("tasklist");
                BufferedReader reader = new BufferedReader(new InputStreamReader(checkProcess.getInputStream()));

                String line;
                isRunning = false;

                while ((line = reader.readLine()) != null) {
                    if (line.toLowerCase().contains(processName.toLowerCase())) {
                        isRunning = true;
                        LOGGER.log(Level.INFO, "Process is still running: " + line);
                        break;
                    }
                }
                reader.close();

                if (isRunning) {
                    Thread.sleep(checkInterval);
                    timeout -= checkInterval;
                }
            }

            LOGGER.log(Level.INFO, "Process has completed.");
            return true;
        } catch (Exception e) {
            LOGGER.severe("Error while running scheduler task: " + e.getMessage());
            return false;
        }
    }


}
