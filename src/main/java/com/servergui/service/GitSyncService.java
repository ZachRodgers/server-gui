package com.servergui.service;

import com.servergui.config.AppConfig;
import com.servergui.model.LogEntry.Category;
import com.servergui.model.LogEntry.Level;
import com.servergui.util.OpsFile;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Watches player chat for {@code !git} commands and runs git on a configured repository.
 * Opped players only. Supports pull (with optional branch and -silent), version, and help.
 * Pulls are rate-limited to 3 every 90 seconds.
 */
public final class GitSyncService {

    private static final long WINDOW_MS = 90_000L;
    private static final int  MAX_PULLS = 3;
    private static final int  MAX_FILES = 15;
    private static final Pattern VALID_BRANCH = Pattern.compile("^[A-Za-z0-9._/][A-Za-z0-9._/-]*$");

    private final ServerController controller;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "git-sync");
        t.setDaemon(true);
        return t;
    });

    private volatile AppConfig config;
    private final Deque<Long> pullTimes = new ArrayDeque<>();

    public GitSyncService(ServerController controller) {
        this.controller = controller;
    }

    public void reconfigure(AppConfig config) {
        this.config = config;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    /** Entry point wired to {@link ServerController#onChat}. */
    public void handleChat(String player, String text) {
        if (text == null) return;
        String trimmed = text.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith("!git")) return;

        AppConfig cfg = config;
        if (cfg == null || !cfg.gitSyncEnabled()) {
            diag(player + " used !git, but Git Sync is disabled.");
            return;
        }
        Optional<Path> repo = cfg.gitRepositoryPath();
        if (repo.isEmpty()) {
            diag(player + " used !git, but no repository folder is configured.");
            return;
        }
        if (!OpsFile.isOpped(cfg.workingDirectory(), player)) {
            diag(player + " used !git, but is not an operator - ignored.");
            return;
        }

        String[] tokens = trimmed.split("\\s+");
        String sub = tokens.length > 1 ? tokens[1].toLowerCase(Locale.ROOT) : "help";
        switch (sub) {
            case "pull" -> handlePull(player, repo.get(), tokens);
            case "version" -> handleVersion(player, repo.get(), tokens);
            case "help" -> {
                diag(player + " requested !git help.");
                executor.submit(this::runHelp);
            }
            default -> {
                diag(player + " used unknown git command: " + sub);
                broadcast("[Git] Unknown command - try !git help", "red");
            }
        }
    }

    private void handlePull(String player, Path repo, String[] tokens) {
        boolean silent = false;
        String branch = null;
        for (int i = 2; i < tokens.length; i++) {
            if (tokens[i].equalsIgnoreCase("-silent")) silent = true;
            else if (branch == null) branch = tokens[i];
        }
        if (branch != null && !VALID_BRANCH.matcher(branch).matches()) {
            broadcast("[Git] Failed to find branch " + branch, "red");
            diag(player + " requested an invalid branch name: " + branch);
            return;
        }
        if (!allowPull()) {
            long wait = secondsUntilNextPull();
            diag(player + " requested a pull during cooldown (" + wait + "s left).");
            broadcast("[Git] Please wait - " + wait + "s before next sync", "red");
            return;
        }
        diag(player + " requested a pull"
                + (branch != null ? " of branch " + branch : "")
                + (silent ? " (silent)" : "") + ".");
        String targetBranch = branch;
        boolean quiet = silent;
        executor.submit(() -> runPull(repo, targetBranch, quiet));
    }

    private void handleVersion(String player, Path repo, String[] tokens) {
        int count = 1;
        if (tokens.length > 2) {
            try {
                int n = Integer.parseInt(tokens[2]);
                if (n >= 1 && n <= 20) count = n;
            } catch (NumberFormatException ignored) {
            }
        }
        diag(player + " requested !git version " + count + ".");
        int requested = count;
        executor.submit(() -> runVersion(repo, requested));
    }

    private void runPull(Path repo, String requestedBranch, boolean silent) {
        if (requestedBranch != null) {
            if (!branchExists(repo, requestedBranch)) {
                broadcast("[Git] Failed to find branch " + requestedBranch, "red");
                diag("Branch not found: " + requestedBranch);
                return;
            }
            GitResult checkout = runGit(repo, "checkout", requestedBranch);
            logLines(checkout);
            if (!checkout.ok()) {
                broadcast("[Git] Failed to switch to branch " + requestedBranch + " - see console", "red");
                return;
            }
        }

        String branch = runGit(repo, "rev-parse", "--abbrev-ref", "HEAD").first("unknown");
        String oldHead = runGit(repo, "rev-parse", "HEAD").first("");
        if (!silent) broadcast("[Git] Pulling " + branch + "...", "yellow");

        GitResult pull = runGit(repo, "pull");
        logLines(pull);
        if (!pull.ok()) {
            broadcast("[Git] Sync failed - see console", "red");
            return;
        }

        String newHead = runGit(repo, "rev-parse", "HEAD").first("");
        int commits = parseInt(runGit(repo, "rev-list", "--count", oldHead + ".." + newHead).first("0"));
        if (commits == 0) {
            if (!silent) broadcast("[Git] Already up to date on branch " + branch, "green");
            return;
        }

        if (!silent) {
            broadcast("[Git] Synced " + commits + " commit" + (commits == 1 ? "" : "s") + " on branch " + branch, "green");
            broadcastChangedFiles(repo, oldHead, newHead);
        }
        if (controller.isRunning()) {
            controller.sendBackground("reload");
            if (!silent) broadcast("[Git] Reloaded", "green");
        }
    }

    private void runVersion(Path repo, int count) {
        String branch = runGit(repo, "rev-parse", "--abbrev-ref", "HEAD").first("unknown");
        GitResult log = runGit(repo, "log", "-n", String.valueOf(count), "--pretty=format:%h %s");
        if (!log.ok()) {
            broadcast("[Git] Could not read history - see console", "red");
            logLines(log);
            return;
        }
        broadcast("[Git] Branch " + branch + ", latest commit" + (count == 1 ? "" : "s") + ":", "green");
        for (String line : log.lines()) {
            if (!line.isBlank()) broadcast(line, "white");
        }
    }

    private void runHelp() {
        broadcast("[Git] Commands:", "green");
        broadcast("!git pull - pull the active branch and reload", "white");
        broadcast("!git pull <branch> - switch to that branch and pull", "white");
        broadcast("!git pull -silent - pull without posting in chat", "white");
        broadcast("!git version [n] - show the latest commit(s), n = 1-20", "white");
        broadcast("!git help - show this list", "white");
    }

    private boolean branchExists(Path repo, String branch) {
        if (runGit(repo, "rev-parse", "--verify", "--quiet", "refs/heads/" + branch).ok()) return true;
        GitResult remote = runGit(repo, "ls-remote", "--heads", "origin", branch);
        return remote.ok() && !remote.raw().isBlank();
    }

    private void broadcastChangedFiles(Path repo, String oldHead, String newHead) {
        List<String> changes = runGit(repo, "diff", "--name-status", oldHead, newHead).lines().stream()
                .filter(line -> !line.isBlank())
                .toList();
        int shown = 0;
        for (String line : changes) {
            if (shown >= MAX_FILES) {
                broadcast("[Git] ...and " + (changes.size() - shown) + " more", "gray");
                break;
            }
            String[] parts = line.split("\t");
            char status = parts[0].isEmpty() ? '?' : parts[0].charAt(0);
            String fileName = parts[parts.length - 1];
            broadcastParts(
                    jsonText(status + " ", statusColor(status)),
                    jsonText(fileName, "white"));
            shown++;
        }
    }

    private static String statusColor(char status) {
        return switch (status) {
            case 'A' -> "green";
            case 'M' -> "yellow";
            case 'D' -> "red";
            case 'R', 'C' -> "aqua";
            default -> "white";
        };
    }

    // Rate limiting: sliding window of recent pulls.

    private synchronized boolean allowPull() {
        long now = System.currentTimeMillis();
        purgeOldPulls(now);
        if (pullTimes.size() >= MAX_PULLS) return false;
        pullTimes.addLast(now);
        return true;
    }

    private synchronized long secondsUntilNextPull() {
        long now = System.currentTimeMillis();
        purgeOldPulls(now);
        if (pullTimes.isEmpty()) return 0;
        long remaining = WINDOW_MS - (now - pullTimes.peekFirst());
        return Math.max(1, (remaining + 999) / 1000);
    }

    private void purgeOldPulls(long now) {
        while (!pullTimes.isEmpty() && now - pullTimes.peekFirst() >= WINDOW_MS) {
            pullTimes.pollFirst();
        }
    }

    // Git execution and chat output.

    private GitResult runGit(Path repo, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(repo.toString());
        command.addAll(Arrays.asList(args));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) lines.add(line);
            }
            int exit = process.waitFor();
            return new GitResult(exit, lines);
        } catch (IOException ex) {
            controller.logExternal(Level.ERROR, Category.SYSTEM, "[Git] Failed to run git: " + ex.getMessage());
            return new GitResult(-1, List.of());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new GitResult(-1, List.of());
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void logLines(GitResult result) {
        for (String line : result.lines()) {
            if (!line.isBlank()) controller.logExternal(Level.INFO, Category.SYSTEM, "[Git] " + line);
        }
    }

    /** Write a Git Sync diagnostic line to the wrapper console (not visible in-game). */
    private void diag(String message) {
        controller.logExternal(Level.INFO, Category.SYSTEM, "[Git] " + message);
    }

    private void broadcast(String text, String color) {
        controller.sendBackground("tellraw @a " + jsonText(text, color));
    }

    private void broadcastParts(String... components) {
        StringBuilder sb = new StringBuilder("[\"\"");
        for (String component : components) sb.append(",").append(component);
        sb.append("]");
        controller.sendBackground("tellraw @a " + sb);
    }

    private static String jsonText(String text, String color) {
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"text\":\"" + escaped + "\",\"color\":\"" + color + "\"}";
    }

    private record GitResult(int exit, List<String> lines) {
        boolean ok() {
            return exit == 0;
        }

        String raw() {
            return String.join("\n", lines);
        }

        String first(String fallback) {
            return lines.isEmpty() ? fallback : lines.get(0);
        }
    }
}
