package com.isoburn.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Service
public class CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(CommandExecutor.class);

    public record CommandResult(int exitCode, String stdout, String stderr) {
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile Process currentProcess;

    public void reset() {
        cancelled.set(false);
        currentProcess = null;
    }

    public void cancel() {
        cancelled.set(true);
        if (currentProcess != null) {
            currentProcess.destroyForcibly();
        }
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public CommandResult execute(String... command) throws IOException, InterruptedException {
        return execute(null, null, command);
    }

    public CommandResult execute(Consumer<String> stdoutHandler, Consumer<String> stderrHandler,
                                  String... command) throws IOException, InterruptedException {
        if (cancelled.get()) {
            return new CommandResult(-1, "", "Cancelled");
        }

        log.debug("Executing command: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);

        currentProcess = pb.start();

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        Thread stdoutThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(currentProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdout.append(line).append("\n");
                    if (stdoutHandler != null) {
                        stdoutHandler.accept(line);
                    }
                }
            } catch (IOException e) {
                log.error("Error reading stdout", e);
            }
        });

        Thread stderrThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(currentProcess.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderr.append(line).append("\n");
                    if (stderrHandler != null) {
                        stderrHandler.accept(line);
                    }
                }
            } catch (IOException e) {
                log.error("Error reading stderr", e);
            }
        });

        stdoutThread.start();
        stderrThread.start();

        boolean completed = currentProcess.waitFor(30, TimeUnit.MINUTES);
        stdoutThread.join(5000);
        stderrThread.join(5000);

        if (!completed) {
            currentProcess.destroyForcibly();
            return new CommandResult(-1, stdout.toString(), "Command timed out");
        }

        int exitCode = currentProcess.exitValue();
        currentProcess = null;

        log.debug("Command completed with exit code: {}", exitCode);
        return new CommandResult(exitCode, stdout.toString().trim(), stderr.toString().trim());
    }

    public CommandResult executeWithSudo(String command) throws IOException, InterruptedException {
        if (cancelled.get()) {
            return new CommandResult(-1, "", "Cancelled");
        }

        log.info("Executing privileged command via osascript");

        String escapedCommand = command.replace("\\", "\\\\").replace("\"", "\\\"");

        String appleScript = String.format(
            "do shell script \"%s\" with administrator privileges",
            escapedCommand
        );

        return execute("osascript", "-e", appleScript);
    }

    public CommandResult executeShell(String command) throws IOException, InterruptedException {
        return execute("/bin/bash", "-c", command);
    }

    public CommandResult executeShell(Consumer<String> stdoutHandler, Consumer<String> stderrHandler,
                                       String command) throws IOException, InterruptedException {
        return execute(stdoutHandler, stderrHandler, "/bin/bash", "-c", command);
    }
}
