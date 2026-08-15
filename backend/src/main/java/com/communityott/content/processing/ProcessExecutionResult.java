package com.communityott.content.processing;

public record ProcessExecutionResult(
        int exitCode,
        String stdout,
        String stderr,
        boolean timedOut,
        long durationMs
) {
    public boolean isSuccess() {
        return !timedOut && exitCode == 0;
    }
}
