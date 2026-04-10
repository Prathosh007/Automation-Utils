package com.me.util.command;

/**
 * Interface for processing command output
 */
public interface CommandOutputProcessor {

    /**
     * Process the output of a command execution
     *
     * @param result The command result to process
     * @return The processed output
     */
    String processOutput(CommandResult result);

    /**
     * Get a descriptive name for this processor
     *
     * @return Processor name
     */
    String getProcessorName();
}
