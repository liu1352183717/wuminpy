package com.wumin.wuminpy.util;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CommandExecutor {
    private static final String TAG = "CommandExecutor";
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    public static class Result {
        public final int exitCode;
        public final List<String> outputLines;
        public final List<String> errorLines;
        public final long executionTimeMs;
        public final Exception exception;

        public Result(int exitCode, List<String> outputLines, List<String> errorLines, long executionTimeMs) {
            this.exitCode = exitCode;
            this.outputLines = outputLines;
            this.errorLines = errorLines;
            this.executionTimeMs = executionTimeMs;
            this.exception = null;
        }

        public Result(Exception e, long executionTimeMs) {
            this.exitCode = -1;
            this.outputLines = new ArrayList<>();
            this.errorLines = new ArrayList<>();
            this.executionTimeMs = executionTimeMs;
            this.exception = e;
        }

        public boolean isSuccess() {
            return exception == null && exitCode == 0;
        }
    }

    public interface Callback {
        void onStart();
        void onOutputLine(String line);
        void onErrorLine(String line);
        void onComplete(int exitCode, long executionTimeMs);
        void onError(Exception e);
    }

    public interface LineCallback {
        void onOutputLine(String line);
        void onErrorLine(String line);
    }

    // 异步执行
    public static Future<?> executeAsync(
            String[] command,
            File workingDir,
            Map<String, String> environment,
            long timeoutSeconds,
            boolean mergeStreams,
            Callback callback) {

        return executor.submit(() -> {
            long startTime = System.currentTimeMillis();
            Process process = null;
            try {
                if (callback != null) callback.onStart();

                ProcessBuilder pb = new ProcessBuilder(command);
                if (workingDir != null) pb.directory(workingDir);
                if (environment != null) pb.environment().putAll(environment);
                pb.redirectErrorStream(mergeStreams);

                process = pb.start();

                Process finalProcess = process;
                Thread outputReader = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(finalProcess.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (callback != null) callback.onOutputLine(line);
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error reading output stream", e);
                    }
                });
                outputReader.start();

                Thread errorReader = null;
                if (!mergeStreams) {
                    Process finalProcess1 = process;
                    errorReader = new Thread(() -> {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(finalProcess1.getErrorStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (callback != null) callback.onErrorLine(line);
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Error reading error stream", e);
                        }
                    });
                    errorReader.start();
                }

                int exitCode;
                if (timeoutSeconds > 0) {
                    boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                    if (!finished) {
                        process.destroyForcibly();
                        throw new TimeoutException("Command timeout after " + timeoutSeconds + "s");
                    }
                    exitCode = process.exitValue();
                } else {
                    exitCode = process.waitFor();
                }

                outputReader.join(1000);
                if (errorReader != null) errorReader.join(1000);

                long execTime = System.currentTimeMillis() - startTime;
                if (callback != null) callback.onComplete(exitCode, execTime);

            } catch (Exception e) {
                if (process != null) process.destroyForcibly();
                if (callback != null) callback.onError(e);
                Log.e(TAG, "Command execution error", e);
            }
        });
    }

    // 同步执行（返回完整结果）
    public static Result executeSync(
            String[] command,
            File workingDir,
            Map<String, String> environment,
            long timeoutSeconds,
            boolean mergeStreams) {

        long startTime = System.currentTimeMillis();
        List<String> outputLines = new ArrayList<>();
        List<String> errorLines = new ArrayList<>();
        Process process = null;

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            if (workingDir != null) pb.directory(workingDir);
            if (environment != null) pb.environment().putAll(environment);
            pb.redirectErrorStream(mergeStreams);

            process = pb.start();

            Process finalProcess = process;
            Thread outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(finalProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) outputLines.add(line);
                } catch (IOException e) {
                    Log.e(TAG, "Error reading output stream", e);
                }
            });
            outputReader.start();

            Thread errorReader = null;
            if (!mergeStreams) {
                Process finalProcess1 = process;
                errorReader = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(finalProcess1.getErrorStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) errorLines.add(line);
                    } catch (IOException e) {
                        Log.e(TAG, "Error reading error stream", e);
                    }
                });
                errorReader.start();
            }

            int exitCode;
            if (timeoutSeconds > 0) {
                boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new TimeoutException("Command timeout after " + timeoutSeconds + "s");
                }
                exitCode = process.exitValue();
            } else {
                exitCode = process.waitFor();
            }

            outputReader.join(1000);
            if (errorReader != null) errorReader.join(1000);

            long execTime = System.currentTimeMillis() - startTime;
            return new Result(exitCode, outputLines, errorLines, execTime);

        } catch (Exception e) {
            if (process != null) process.destroyForcibly();
            long execTime = System.currentTimeMillis() - startTime;
            return new Result(e, execTime);
        }
    }

    // 同步执行并实时回调
    public static void executeSyncWithCallback(
            String[] command,
            File workingDir,
            Map<String, String> environment,
            long timeoutSeconds,
            boolean mergeStreams,
            LineCallback callback) throws IOException, InterruptedException, TimeoutException {

        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDir != null) pb.directory(workingDir);
        if (environment != null) pb.environment().putAll(environment);
        pb.redirectErrorStream(mergeStreams);

        Process process = pb.start();

        Thread outputReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (callback != null) callback.onOutputLine(line);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error reading output stream", e);
            }
        });
        outputReader.start();

        Thread errorReader = null;
        if (!mergeStreams) {
            errorReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (callback != null) callback.onErrorLine(line);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error reading error stream", e);
                }
            });
            errorReader.start();
        }

        int exitCode;
        if (timeoutSeconds > 0) {
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new TimeoutException("Command timeout after " + timeoutSeconds + "s");
            }
            exitCode = process.exitValue();
        } else {
            exitCode = process.waitFor();
        }

        outputReader.join(1000);
        if (errorReader != null) errorReader.join(1000);

        if (exitCode != 0) {
            throw new IOException("Command exited with code: " + exitCode);
        }
    }
}