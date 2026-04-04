package com.zach.minecraft.servergui.service;

import com.zach.minecraft.servergui.config.AppConfig;
import com.zach.minecraft.servergui.model.HeapSample;
import com.zach.minecraft.servergui.model.LogEntry;
import com.zach.minecraft.servergui.model.LogEntry.Category;
import com.zach.minecraft.servergui.model.LogEntry.Level;
import com.zach.minecraft.servergui.util.AnsiUtil;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerController {

    public enum ServerStatus { OFFLINE, LOADING, ONLINE }

    private static final Pattern LIST_PATTERN  = Pattern.compile("There are \\d+ of a max of \\d+ players online:?\\s*(.*)");
    private static final Pattern JOIN_PATTERN  = Pattern.compile("([A-Za-z0-9_]{3,16}) joined the game");
    private static final Pattern LEAVE_PATTERN = Pattern.compile("([A-Za-z0-9_]{3,16}) left the game");
    private static final Pattern TPS_PATTERN   = Pattern.compile("TPS.*?:\\s*([0-9.]+)");
    private static final Pattern HEAP_PATTERN  = Pattern.compile("(?:\\S+\\s+)*heap\\s+total\\s+(\\d+)([KMG]),\\s+used\\s+(\\d+)([KMG])", Pattern.CASE_INSENSITIVE);
    private static final Pattern BODY_PREFIX_PATTERN = Pattern.compile("^(?:\\[[^\\]]+/(?:TRACE|DEBUG|INFO|WARN|ERROR|FATAL)]\\s*:?[ ]*)+");
    // Matches player chat after stripping server thread prefixes
    private static final Pattern CHAT_PATTERN      = Pattern.compile("(?:^|\\s)<?[A-Za-z0-9_]{3,16}>\\s|^\\[Not Secure]\\s*<[A-Za-z0-9_]{3,16}>");
    // Matches player-issued /commands logged by the server
    private static final Pattern PLAYER_CMD_PATTERN = Pattern.compile("issued server command:");
    // Strip the [HH:MM:SS LEVEL]: prefix before category classification
    private static final Pattern MC_HEADER_STRIP   = Pattern.compile("^\\[\\d{2}:\\d{2}:\\d{2} [A-Z]+]: ?");
    // Lines that are purely the server's stdin prompt artifact — suppress them
    private static final Pattern JUNK_PATTERN      = Pattern.compile("^[>\\s]*$");

    private final AppConfig config;
    private final ScheduledExecutorService scheduler  = Executors.newScheduledThreadPool(4);
    private final ExecutorService           ioExecutor = Executors.newCachedThreadPool();
    private final Set<String>               players    = ConcurrentHashMap.newKeySet();
    private final List<ScheduledFuture<?>>  scheduledTasks = new ArrayList<>();

    private volatile Consumer<LogEntry>      logListener    = e -> {};
    private volatile Consumer<List<String>>  playerListener = l -> {};
    private volatile Consumer<Double>        tpsListener    = v -> {};
    private volatile Consumer<HeapSample>    heapListener   = s -> {};
    private volatile Consumer<ServerStatus>  statusListener = s -> {};

    private volatile Process       process;
    private volatile BufferedWriter processInput;
    private volatile boolean        running;
    private volatile int            generation;
    private final AtomicInteger     pendingSilentPlayerPolls = new AtomicInteger();
    private final AtomicInteger     pendingSilentTpsPolls = new AtomicInteger();
    private volatile boolean        heapUnavailableLogged;

    public ServerController(AppConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    public void onLog(Consumer<LogEntry> l)          { logListener    = l; }
    public void onPlayers(Consumer<List<String>> l)  { playerListener = l; }
    public void onTps(Consumer<Double> l)            { tpsListener    = l; }
    public void onHeap(Consumer<HeapSample> l)       { heapListener   = l; }
    public void onStatus(Consumer<ServerStatus> l)   { statusListener = l; }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    public synchronized void start() {
        if (running) return;
        cancelScheduledTasks();
        players.clear();
        heapUnavailableLogged = false;
        generation++;
        running = true;
        statusListener.accept(ServerStatus.LOADING);

        if (config.mockMode()) {
            startMockServer();
            return;
        }

        try {
            ProcessBuilder builder = new ProcessBuilder(config.commandTokens());
            builder.directory(config.workingDirectory().toFile());
            process = builder.start();
            processInput = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            emitLog(Level.INFO, Category.SYSTEM, "Launching: " + config.serverCommand());
            ioExecutor.submit(() -> streamLogs(process.inputReader(StandardCharsets.UTF_8), false));
            ioExecutor.submit(() -> streamLogs(process.errorReader(StandardCharsets.UTF_8), true));
            ioExecutor.submit(this::waitForExit);
            schedulePollers();
        } catch (IOException ex) {
            emitLog(Level.ERROR, Category.SYSTEM, "Failed to launch server: " + ex.getMessage());
            running = false;
            statusListener.accept(ServerStatus.OFFLINE);
        }
    }

    public synchronized void stop() {
        if (!running) return;
        cancelScheduledTasks();
        try { sendCommandRaw("stop"); } catch (Exception ignored) {}
        if (process != null && process.isAlive()) process.destroy();
        if (config.mockMode()) emitLog(Level.INFO, Category.SYSTEM, "Mock server stopped.");
        running = false;
        statusListener.accept(ServerStatus.OFFLINE);
    }

    public synchronized void forceKill() {
        if (!running) return;
        cancelScheduledTasks();
        generation++;           // prevent stale waitForExit from re-firing
        running = false;
        statusListener.accept(ServerStatus.OFFLINE);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            emitLog(Level.WARN, Category.SYSTEM, "Server process force-killed.");
        }
        if (config.mockMode()) emitLog(Level.WARN, Category.SYSTEM, "Mock server force-killed.");
    }

    public void restart() {
        stop();
        start();
    }

    public synchronized void shutdown() {
        stop();
        scheduler.shutdownNow();
        ioExecutor.shutdownNow();
    }

    public void sendCommand(String command) {
        if (!running || command == null || command.isBlank()) return;
        emitLog(Level.INFO, Category.COMMAND, "> " + command);
        pollSilently(command);  // reuse mock/real dispatch, no second echo
    }

    /** Scheduled-poller variant — sends without echoing to the console log. */
    private void pollSilently(String command) {
        if (!running) return;
        if ("list".equalsIgnoreCase(command)) pendingSilentPlayerPolls.incrementAndGet();
        else if ("tps".equalsIgnoreCase(command)) pendingSilentTpsPolls.incrementAndGet();

        if (config.mockMode()) {
            if ("list".equalsIgnoreCase(command)) {
                handleLine("There are " + players.size() +
                        " of a max of 20 players online: " + String.join(", ", players), false);
            } else if ("tps".equalsIgnoreCase(command)) {
                handleLine("TPS from last 1m, 5m, 15m: 19.98, 19.96, 20.00", false);
            }
            return;
        }
        sendCommandRaw(command);
    }

    // Send to process stdin without echoing to log (used internally)
    private void sendCommandRaw(String command) {
        if (processInput == null) return;
        try {
            processInput.write(command);
            processInput.newLine();
            processInput.flush();
        } catch (IOException ex) {
            emitLog(Level.ERROR, Category.SYSTEM, "Failed to send command: " + ex.getMessage());
        }
    }

    // ── Log handling ───────────────────────────────────────────────────────

    private void streamLogs(BufferedReader reader, boolean stderr) {
        reader.lines().forEach(line -> handleLine(line, stderr));
    }

    private void handleLine(String line, boolean stderr) {
        String stripped = AnsiUtil.strip(line).trim();

        // Drop blank lines and bare server-prompt artifacts ("> ", "> > ", etc.)
        if (stripped.isEmpty() || JUNK_PATTERN.matcher(stripped).matches()) return;

        parsePlayers(stripped);
        parseTps(stripped);
        detectOnline(stripped);

        if (shouldSuppressPollResult(stripped)) return;

        Level level = classifyLevel(stripped, stderr);
        Category category = classifyCategory(stripped);
        emitLog(level, category, line);  // pass raw (ANSI) line to UI
    }

    private boolean shouldSuppressPollResult(String line) {
        if (LIST_PATTERN.matcher(line).find() && pendingSilentPlayerPolls.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
            return true;
        }
        if (TPS_PATTERN.matcher(line).find() && pendingSilentTpsPolls.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
            return true;
        }
        return false;
    }

    private void parsePlayers(String line) {
        Matcher listMatcher = LIST_PATTERN.matcher(line);
        if (listMatcher.find()) {
            players.clear();
            String payload = listMatcher.group(1).trim();
            if (!payload.isBlank()) {
                for (String name : payload.split(",")) {
                    String trimmed = AnsiUtil.strip(name).trim();
                    if (!trimmed.isEmpty()) players.add(trimmed);
                }
            }
            playerListener.accept(players.stream().sorted().toList());
            return;
        }
        Matcher join = JOIN_PATTERN.matcher(line);
        if (join.find()) {
            players.add(join.group(1));
            playerListener.accept(players.stream().sorted().toList());
            return;
        }
        Matcher leave = LEAVE_PATTERN.matcher(line);
        if (leave.find()) {
            players.remove(leave.group(1));
            playerListener.accept(players.stream().sorted().toList());
        }
    }

    private void parseTps(String line) {
        Matcher m = TPS_PATTERN.matcher(line);
        if (m.find()) {
            try { tpsListener.accept(Double.parseDouble(m.group(1))); }
            catch (NumberFormatException ignored) {}
        }
    }

    private void detectOnline(String stripped) {
        if (stripped.contains("Done (") && stripped.contains("For help")) {
            statusListener.accept(ServerStatus.ONLINE);
        }
    }

    // ── Process lifecycle ──────────────────────────────────────────────────

    private void waitForExit() {
        int gen = generation;
        try {
            if (process != null) {
                int code = process.waitFor();
                emitLog(Level.WARN, Category.SYSTEM, "Server exited with code " + code);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            synchronized (this) {
                if (generation == gen) {
                    running = false;
                    statusListener.accept(ServerStatus.OFFLINE);
                }
            }
        }
    }

    private void schedulePollers() {
        if (config.playerPollSeconds() > 0) {
            scheduledTasks.add(scheduler.scheduleAtFixedRate(
                    () -> pollSilently("list"),   // no echo — routine poll
                    config.playerPollSeconds(), config.playerPollSeconds(), TimeUnit.SECONDS));
        }
        if (config.tpsPollSeconds() > 0) {
            scheduledTasks.add(scheduler.scheduleAtFixedRate(
                    () -> pollSilently("tps"),    // no echo — routine poll
                    config.tpsPollSeconds(), config.tpsPollSeconds(), TimeUnit.SECONDS));
        }
        if (config.heapPollSeconds() > 0) {
            scheduledTasks.add(scheduler.scheduleAtFixedRate(
                    this::pollHeapUsage,
                    config.heapPollSeconds(), config.heapPollSeconds(), TimeUnit.SECONDS));
        }
    }

    private void pollHeapUsage() {
        Optional<ProcessHandle> javaProcess = resolveTargetJavaProcess();
        if (!running || javaProcess.isEmpty()) return;
        try {
            Process jcmd = new ProcessBuilder("jcmd", String.valueOf(javaProcess.get().pid()), "GC.heap_info")
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(jcmd.getInputStream(), StandardCharsets.UTF_8))) {
                output = r.lines().reduce("", (a, b) -> a + "\n" + b);
            }
            int exitCode = jcmd.waitFor();
            if (exitCode != 0 || output.isBlank()) {
                logHeapUnavailableOnce("Heap polling unavailable: jcmd could not inspect the server JVM.");
                return;
            }

            Matcher m = HEAP_PATTERN.matcher(output);
            if (m.find()) {
                double totalMb = toMb(Integer.parseInt(m.group(1)), m.group(2));
                double usedMb  = toMb(Integer.parseInt(m.group(3)), m.group(4));
                heapListener.accept(new HeapSample(usedMb, totalMb));
            } else {
                logHeapUnavailableOnce("Heap polling unavailable: unsupported jcmd heap output.");
            }
        } catch (IOException ignored) {
            logHeapUnavailableOnce("Heap polling unavailable: jcmd could not be started.");
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void startMockServer() {
        emitLog(Level.INFO, Category.SYSTEM, "Mock mode enabled. Edit server-wrapper.properties to launch a real server.");
        scheduledTasks.add(scheduler.scheduleAtFixedRate(new Runnable() {
            private int tick;
            @Override public void run() {
                if (!running) return;
                tick++;
                if (tick == 1) {
                    emitLog(Level.INFO, Category.SYSTEM, "[Server thread/INFO]: Preparing spawn area");
                } else if (tick == 2) {
                    emitLog(Level.INFO, Category.SYSTEM, "[Server thread/INFO]: Done (2.381s)! For help, type \"help\"");
                    statusListener.accept(ServerStatus.ONLINE);
                } else if (tick % 6 == 0) {
                    String player = tick % 12 == 0 ? "Alex" : "Steve";
                    players.add(player);
                    playerListener.accept(players.stream().sorted().toList());
                    emitLog(Level.INFO, Category.SYSTEM, player + " joined the game");
                } else if (tick % 9 == 0 && !players.isEmpty()) {
                    String player = players.iterator().next();
                    players.remove(player);
                    playerListener.accept(players.stream().sorted().toList());
                    emitLog(Level.INFO, Category.SYSTEM, player + " left the game");
                } else if (tick % 4 == 0) {
                    emitLog(Level.INFO, Category.CHAT, "<Steve> hello everyone");
                } else {
                    emitLog(Level.INFO, Category.SYSTEM, "[Server thread/INFO]: Autosave complete in " + (40 + tick) + " ms");
                }
                double tps  = 19.7 + ((tick % 5) * 0.06);
                double used = 1024 + ((tick % 8) * 64);
                tpsListener.accept(tps);
                heapListener.accept(new HeapSample(used, 4096));
            }
        }, 0, 3, TimeUnit.SECONDS));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void emitLog(Level level, Category category, String message) {
        logListener.accept(new LogEntry(LocalTime.now(), level, category, message));
    }

    private void cancelScheduledTasks() {
        scheduledTasks.forEach(t -> t.cancel(true));
        scheduledTasks.clear();
        pendingSilentPlayerPolls.set(0);
        pendingSilentTpsPolls.set(0);
    }

    private static Level classifyLevel(String stripped, boolean stderr) {
        if (stderr) return Level.ERROR;
        String lower = stripped.toLowerCase(Locale.ROOT);
        if (lower.contains("error") || lower.contains("exception")) return Level.ERROR;
        if (lower.contains("warn")) return Level.WARN;
        return Level.INFO;
    }

    private static Category classifyCategory(String stripped) {
        String body = normalizeMessageBody(stripped);
        if (CHAT_PATTERN.matcher(body).find())      return Category.CHAT;
        if (PLAYER_CMD_PATTERN.matcher(body).find()) return Category.COMMAND;
        return Category.SYSTEM;
    }

    private Optional<ProcessHandle> resolveTargetJavaProcess() {
        Process cur = process;
        if (!running || cur == null || !cur.isAlive()) return Optional.empty();

        ProcessHandle root = cur.toHandle();
        return root.descendants()
                .filter(ProcessHandle::isAlive)
                .filter(ServerController::isJavaProcess)
                .findFirst()
                .or(() -> isJavaProcess(root) ? Optional.of(root) : Optional.empty());
    }

    private static boolean isJavaProcess(ProcessHandle handle) {
        return handle.info().command()
                .map(command -> command.toLowerCase(Locale.ROOT).contains("java"))
                .orElse(false);
    }

    private static double toMb(int value, String unit) {
        return switch (unit.toUpperCase(Locale.ROOT)) {
            case "G" -> value * 1024.0;
            case "M" -> value;
            default -> value / 1024.0;
        };
    }

    private void logHeapUnavailableOnce(String message) {
        if (!heapUnavailableLogged) {
            heapUnavailableLogged = true;
            emitLog(Level.WARN, Category.SYSTEM, message);
        }
    }

    private static String normalizeMessageBody(String stripped) {
        String body = MC_HEADER_STRIP.matcher(stripped).replaceFirst("").trim();
        body = BODY_PREFIX_PATTERN.matcher(body).replaceFirst("").trim();
        return body;
    }
}
