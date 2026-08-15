package com.communityott.content.processing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class DefaultProcessRunner implements ProcessRunner {

    private static final int MAX_STREAM_CHARS = 1024 * 1024; // 1 MB max memory per stream

    @Override
    public ProcessExecutionResult execute(List<String> commandArgs, int timeoutSeconds) {
        if (commandArgs == null || commandArgs.isEmpty()) {
            throw new IllegalArgumentException("Command arguments must not be null or empty");
        }

        long startTime = System.currentTimeMillis();
        ProcessBuilder processBuilder = new ProcessBuilder(commandArgs);

        Process process = null;
        try {
            process = processBuilder.start();

            // Asynchronously capture stdout and stderr to prevent buffer deadlocks
            CompletableFuture<String> stdoutFuture = readStreamAsync(process.getInputStream());
            CompletableFuture<String> stderrFuture = readStreamAsync(process.getErrorStream());

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            long durationMs = System.currentTimeMillis() - startTime;

            if (!finished) {
                log.warn("Process timed out after {}s: {}", timeoutSeconds, commandArgs.get(0));
                process.destroyForcibly();
                String stdout = stdoutFuture.getNow("[Timed out]");
                String stderr = stderrFuture.getNow("[Timed out]");
                return new ProcessExecutionResult(-1, stdout, stderr, true, durationMs);
            }

            int exitCode = process.exitValue();
            String stdout = stdoutFuture.join();
            String stderr = stderrFuture.join();

            return new ProcessExecutionResult(exitCode, stdout, stderr, false, durationMs);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Process execution interrupted for {}: {}", commandArgs.get(0), e.getMessage());
            if (process != null) {
                process.destroyForcibly();
            }
            return new ProcessExecutionResult(-1, "", "Process interrupted: " + e.getMessage(), true, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Failed to execute process {}: {}", commandArgs.get(0), e.getMessage(), e);
            if (process != null) {
                process.destroyForcibly();
            }
            return new ProcessExecutionResult(-1, "", "Execution error: " + e.getMessage(), false, System.currentTimeMillis() - startTime);
        }
    }

    private CompletableFuture<String> readStreamAsync(InputStream inputStream) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                char[] buffer = new char[4096];
                int charsRead;
                while ((charsRead = reader.read(buffer)) != -1) {
                    if (sb.length() + charsRead <= MAX_STREAM_CHARS) {
                        sb.append(buffer, 0, charsRead);
                    } else if (sb.length() < MAX_STREAM_CHARS) {
                        int remaining = MAX_STREAM_CHARS - sb.length();
                        sb.append(buffer, 0, remaining);
                        sb.append("\n... [Output truncated at 1MB limit]");
                    }
                }
            } catch (Exception e) {
                log.debug("Error reading stream from child process: {}", e.getMessage());
            }
            return sb.toString();
        });
    }
}
