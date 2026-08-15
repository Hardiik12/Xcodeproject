package com.communityott.content.processing;

import java.util.List;

public interface ProcessRunner {

    /**
     * Executes the given command arguments securely in an isolated process.
     *
     * @param commandArgs List of arguments (must never be concatenated shell strings)
     * @param timeoutSeconds Timeout in seconds before terminating the process forcibly
     * @return ProcessExecutionResult capturing exit code, streams, timeout status, and duration
     */
    ProcessExecutionResult execute(List<String> commandArgs, int timeoutSeconds);
}
