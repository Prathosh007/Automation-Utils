package com.me.util.command;

/**
 * Represents the result of a command execution, including the command executed,
 * output, success status, and exit code.
 */
public class CommandResult {
    private final String command;
    private final String output;
    private final boolean success;
    private final int exitCode;
    private final boolean timedOut;
    private final Exception error;

    /**
     * Create a command result
     *
     * @param command The command that was executed
     * @param output The output from the command execution
     * @param success Whether the command executed successfully
     * @param exitCode The exit code returned by the command
     */
    public CommandResult(String command, String output, boolean success, int exitCode) {
        this.command = command;
        this.output = output;
        this.success = success;
        this.exitCode = exitCode;
        this.timedOut = false;
        this.error = null;
    }

    /**
     * Create a command result for an error case
     *
     * @param command The command that was attempted
     * @param error The exception that occurred
     */
    private CommandResult(String command, String output, boolean success, int exitCode, boolean timedOut, Exception error) {
        this.command = command;
        this.output = output;
        this.success = success;
        this.exitCode = exitCode;
        this.timedOut = timedOut;
        this.error = error;
    }

    /**
     * Create a result for a command that timed out
     *
     * @param command The command that timed out
     * @return CommandResult indicating timeout
     */
    public static CommandResult timeout(String command) {
        return new CommandResult(command, "Command execution timed out", false, -1, true, null);
    }

    /**
     * Create a result for a command that encountered an error
     *
     * @param command The command that failed
     * @param error The exception that occurred
     * @return CommandResult indicating error
     */
    public static CommandResult error(String command, Exception error) {
        return new CommandResult(
            command,
            error != null ? error.getMessage() : "Unknown error",
            false,
            -1,
            false,
            error
        );
    }

    /**
     * Get the command that was executed
     *
     * @return The command string
     */
    public String getCommand() {
        return command;
    }

    /**
     * Get the output from the command execution
     *
     * @return The command output
     */
    public String getOutput() {
        return output;
    }

    /**
     * Get the trimmed output from the command execution
     *
     * @return The command output with leading and trailing whitespace removed
     */
    public String getTrimmedOutput() {
        return output != null ? output.trim() : "";
    }

    /**
     * Check if the command executed successfully
     *
     * @return true if the command was successful, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Get the exit code returned by the command
     *
     * @return The exit code
     */
    public int getExitCode() {
        return exitCode;
    }

    /**
     * Check if the command execution timed out
     *
     * @return true if the command timed out, false otherwise
     */
    public boolean isTimedOut() {
        return timedOut;
    }

    /**
     * Get the error that occurred during command execution
     *
     * @return The exception that occurred, or null if no error
     */
    public Exception getError() {
        return error;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CommandResult{");
        sb.append("success=").append(success);
        sb.append(", exitCode=").append(exitCode);

        if (timedOut) {
            sb.append(", timedOut=true");
        }

        if (error != null) {
            sb.append(", error=").append(error.getClass().getSimpleName())
              .append(": ").append(error.getMessage());
        }

        sb.append("}");
        return sb.toString();
    }
}
