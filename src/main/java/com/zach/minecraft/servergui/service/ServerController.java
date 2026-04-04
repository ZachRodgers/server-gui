package com.zach.minecraft.servergui.service;

import com.zach.minecraft.servergui.config.AppConfig;
import com.zach.minecraft.servergui.model.HeapSample;
import com.zach.minecraft.servergui.model.LogEntry;
import com.zach.minecraft.servergui.model.LogEntry.Level;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerController {
    private static final Pattern LIST_PATTERN = Pattern.compile("There are \\d+ of a max of \\d+ players online:?\\s*(.*)");
    private static final Pattern JOIN_PATTERN = Pattern.compile("([A-Za-z0-9_]{3,16}) joined the game");
    private static final Pattern LEAVE_PATTERN = Pattern.compile("([A-Za-z0-9_]{3,16}) left the game");
    private static final Pattern TPS_PATTERN = Pattern.compile("TPS.*?:\\s*([0-9.]+)");
    private static final Pattern HEAP_PATTERN = Pattern.compile("heap total\\s+(\\d+)K, used\\s+(\\d+)K");

    private final AppConfig config;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool();
    private final Set<String> players = ConcurrentHashMap.newKeySet();
    private final List<ScheduledFuture<?>> scheduledTasks = new ArrayList<>();

    private volatile Consumer<LogEntry> logListener = entry -> {
    };
    private volatile Consumer<List<String>> playerListener = list -> {
    };
    private volatile Consumer<Double> tpsListener = value -> {
    };
    private volatile Consumer<HeapSample> heapListener = sample -> {
    };
    private volatile Consumer<Boolean> runningListener = running -> {
    };

    private volatile Process process;
    private volatile BufferedWriter processInput;
    private volatile boolean running;

    public ServerController(AppConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    public void onLog(Consumer<LogEntry> listener) {
        this.logListener = listener;
    }

    public void onPlayers(Consumer<List<String>> listener) {
        this.playerListener = listener;
    }

    public void onTps(Consumer<Double> listener) {
        this.tpsListener = listener;
    }

    public void onHeap(Consumer<HeapSample> listener) {
        this.heapListener = listener;
    }

    public void onRunning(Consumer<Boolean> listener) {
        this.runningListener = listener;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        cancelScheduledTasks();
        players.clear();
        running = true;
        runningListener.accept(true);

        if (config.mockMode()) {
            startMockServer();
            return;
        }

        try {
            ProcessBuilder builder = new ProcessBuilder(config.commandTokens());
            builder.directory(config.workingDirectory().toFile());
            process = builder.start();
            processInput = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            emitLog(Level.INFO, "Launching: " + config.serverCommand());
            ioExecutor.submit(() -> streamLogs(process.inputReader(StandardCharsets.UTF_8), false));
            ioExecutor.submit(() -> streamLogs(process.errorReader(StandardCharsets.UTF_8), true));
            ioExecutor.submit(this::waitForExit);
            schedulePollers();
        } catch (IOException exception) {
            emitLog(Level.ERROR, "Failed to launch server: " + exception.getMessage());
            running = false;
            runningListener.accept(false);
        }
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        cancelScheduledTasks();
        try {
            sendCommand("stop");
        } catch (Exception ignored) {
        }
        if (process != null && process.isAlive()) {
            process.destroy();
        }
        if (config.mockMode()) {
            emitLog(Level.INFO, "Mock server stopped.");
        }
        running = false;
        runningListener.accept(false);
    }

    public synchronized void shutdown() {
        stop();
        scheduler.shutdownNow();
        ioExecutor.shutdownNow();
    }

    public void sendCommand(String command) {
        if (!running || command == null || command.isBlank()) {
            return;
        }
        if (config.mockMode()) {
            emitLog(Level.INFO, "> " + command);
            if ("list".equalsIgnoreCase(command.trim())) {
                emitLog(Level.INFO, "There are " + players.size() + " of a max of 20 players online: " + String.join(", ", players));
            } else if ("tps".equalsIgnoreCase(command.trim())) {
                emitLog(Level.INFO, "TPS from last 1m, 5m, 15m: 19.98, 19.96, 20.00");
            }
            return;
        }
        try {
            processInput.write(command);
            processInput.newLine();
            processInput.flush();
            emitLog(Level.INFO, "> " + command);
        } catch (IOException exception) {
            emitLog(Level.ERROR, "Failed to send command: " + exception.getMessage());
        }
    }

    private void streamLogs(BufferedReader reader, boolean stderr) {
        reader.lines().forEach(line -> handleLine(line, stderr));
    }

    private void handleLine(String line, boolean stderr) {
        Level level = classifyLevel(line, stderr);
        emitLog(level, line);
        parsePlayers(line);
        parseTps(line);
    }

    private void parsePlayers(String line) {
        Matcher listMatcher = LIST_PATTERN.matcher(line);
        if (listMatcher.find()) {
            players.clear();
            String payload = listMatcher.group(1).trim();
            if (!payload.isBlank()) {
                for (String name : payload.split(",")) {
                    String trimmed = name.trim();
                    if (!trimmed.isEmpty()) {
                        players.add(trimmed);
                    }
                }
            }
            playerListener.accept(players.stream().sorted().toList());
            return;
        }

        Matcher joinMatcher = JOIN_PATTERN.matcher(line);
        if (joinMatcher.find()) {
            players.add(joinMatcher.group(1));
            playerListener.accept(players.stream().sorted().toList());
            return;
        }

        Matcher leaveMatcher = LEAVE_PATTERN.matcher(line);
        if (leaveMatcher.find()) {
            players.remove(leaveMatcher.group(1));
            playerListener.accept(players.stream().sorted().toList());
        }
    }

    private void parseTps(String line) {
        Matcher matcher = TPS_PATTERN.matcher(line);
        if (matcher.find()) {
            try {
                tpsListener.accept(Double.parseDouble(matcher.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void waitForExit() {
        try {
            if (process != null) {
                int code = process.waitFor();
                emitLog(Level.WARN, "Server exited with code " + code);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            running = false;
            runningListener.accept(false);
        }
    }

    private void schedulePollers() {
        if (config.playerPollSeconds() > 0) {
            scheduledTasks.add(scheduler.scheduleAtFixedRate(
                    () -> sendCommand("list"),
                    config.playerPollSeconds(),
                    config.playerPollSeconds(),
                    TimeUnit.SECONDS
            ));
        }
        if (config.tpsPollSeconds() > 0) {
            scheduledTasks.add(scheduler.scheduleAtFixedRate(
                    () -> sendCommand("tps"),
                    config.tpsPollSeconds(),
                    config.tpsPollSeconds(),
                    TimeUnit.SECONDS
            ));
        }
        if (config.heapPollSeconds() > 0) {
            scheduledTasks.add(scheduler.scheduleAtFixedRate(
                    this::pollHeapUsage,
                    config.heapPollSeconds(),
                    config.heapPollSeconds(),
                    TimeUnit.SECONDS
            ));
        }
    }

    private void pollHeapUsage() {
        Process currentProcess = process;
        if (!running || currentProcess == null || !currentProcess.isAlive()) {
            return;
        }
        ProcessBuilder builder = new ProcessBuilder("jcmd", String.valueOf(currentProcess.pid()), "GC.heap_info");
        try {
            Process jcmd = builder.start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(jcmd.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().reduce("", (left, right) -> left + "\n" + right);
            }
            Matcher matcher = HEAP_PATTERN.matcher(output);
            if (matcher.find()) {
                double totalMb = Integer.parseInt(matcher.group(1)) / 1024.0;
                double usedMb = Integer.parseInt(matcher.group(2)) / 1024.0;
                heapListener.accept(new HeapSample(usedMb, totalMb));
            }
        } catch (IOException ignored) {
            emitLog(Level.WARN, "Heap polling unavailable. Ensure the server process is a local Java process.");
        }
    }

    private void startMockServer() {
        emitLog(Level.INFO, "Mock mode enabled. Change server-wrapper.properties to launch a real server.");
        scheduledTasks.add(scheduler.scheduleAtFixedRate(new Runnable() {
            private int tick;

            @Override
            public void run() {
                if (!running) {
                    return;
                }
                tick++;
                if (tick == 1) {
                    emitLog(Level.INFO, "[Server thread/INFO]: Preparing spawn area");
                } else if (tick == 2) {
                    emitLog(Level.INFO, "[Server thread/INFO]: Done (2.381s)! For help, type \"help\"");
                } else if (tick % 6 == 0) {
                    String player = tick % 12 == 0 ? "Alex" : "Steve";
                    players.add(player);
                    playerListener.accept(players.stream().sorted().toList());
                    emitLog(Level.INFO, player + " joined the game");
                } else if (tick % 9 == 0 && !players.isEmpty()) {
                    String player = players.iterator().next();
                    players.remove(player);
                    playerListener.accept(players.stream().sorted().toList());
                    emitLog(Level.INFO, player + " left the game");
                } else {
                    emitLog(Level.INFO, "[Server thread/INFO]: Autosave complete in " + (40 + tick) + " ms");
                }

                double tps = 19.7 + ((tick % 5) * 0.06);
                double used = 1024 + ((tick % 8) * 64);
                tpsListener.accept(tps);
                heapListener.accept(new HeapSample(used, 4096));
            }
        }, 0, 3, TimeUnit.SECONDS));
    }

    private void emitLog(Level level, String message) {
        logListener.accept(new LogEntry(LocalTime.now(), level, message));
    }

    private void cancelScheduledTasks() {
        scheduledTasks.forEach(task -> task.cancel(true));
        scheduledTasks.clear();
    }

    private static Level classifyLevel(String line, boolean stderr) {
        if (stderr) {
            return Level.ERROR;
        }
        String normalized = line.toLowerCase(Locale.ROOT);
        if (normalized.contains("error") || normalized.contains("exception")) {
            return Level.ERROR;
        }
        if (normalized.contains("warn")) {
            return Level.WARN;
        }
        return Level.INFO;
    }
}
