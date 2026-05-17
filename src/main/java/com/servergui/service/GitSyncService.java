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
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Watches player chat for a {@code !git pull} trigger and runs {@code git pull} on a
 * configured repository. Opped players only, rate-limited to once per minute.
 */
public final class GitSyncService {

    private static final long COOLDOWN_MS = 60_000L;
    // !git pull   or   !git pull reload   (case-insensitive, exact after trim)
    private static final Pattern TRIGGER = Pattern.compile("^!git\\s+pull(\\s+reload)?$", Pattern.CASE_INSENSITIVE);

    private final ServerController controller;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "git-sync");
        t.setDaemon(true);
        return t;
    });

    private volatile AppConfig config;
    private volatile long lastSyncMillis;

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
        var matcher = TRIGGER.matcher(text.trim());
        if (!matcher.matches()) return;
        boolean reload = matcher.group(1) != null;

        // A !git pull was typed — from here, report every outcome to the wrapper console.
        AppConfig cfg = config;
        if (cfg == null || !cfg.gitSyncEnabled()) {
            diag(player + " typed !git pull, but Git Sync is disabled.");
            return;
        }
        Optional<Path> repo = cfg.gitRepositoryPath();
        if (repo.isEmpty()) {
            diag(player + " typed !git pull, but no repository folder is configured.");
            return;
        }
        if (!OpsFile.isOpped(cfg.workingDirectory(), player)) {
            diag(player + " typed !git pull, but is not an operator — ignored.");
            return;
        }

        long now = System.currentTimeMillis();
        long elapsed = now - lastSyncMillis;
        if (lastSyncMillis != 0 && elapsed < COOLDOWN_MS) {
            long remaining = (COOLDOWN_MS - elapsed + 999) / 1000;
            diag(player + " requested a sync during cooldown (" + remaining + "s left).");
            broadcast("[Git] Please wait - " + remaining + "s before next sync", "red");
            return;
        }
        lastSyncMillis = now;
        diag(player + " requested a sync" + (reload ? " + reload." : "."));
        executor.submit(() -> runPull(repo.get(), reload));
    }

    private void runPull(Path repo, boolean reload) {
        controller.logExternal(Level.INFO, Category.SYSTEM, "[Git] Running 'git pull' in " + repo);
        try {
            Process process = new ProcessBuilder("git", "-C", repo.toString(), "pull")
                    .redirectErrorStream(true)
                    .start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                    controller.logExternal(Level.INFO, Category.SYSTEM, "[Git] " + line);
                }
            }
            int exit = process.waitFor();
            if (exit != 0) {
                controller.logExternal(Level.ERROR, Category.SYSTEM, "[Git] git pull exited with code " + exit);
                broadcast("[Git] Sync failed - see console", "red");
                return;
            }

            boolean upToDate = output.toString().toLowerCase(Locale.ROOT).contains("already up to date");
            broadcast(upToDate ? "[Git] Already up to date" : "[Git] Repository synced", "green");

            if (reload) {
                controller.sendBackground("reload");
                broadcast("[Git] Reloaded", "green");
            }
        } catch (IOException ex) {
            controller.logExternal(Level.ERROR, Category.SYSTEM, "[Git] Failed to run git: " + ex.getMessage());
            broadcast("[Git] Sync failed - see console", "red");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /** Write a Git Sync diagnostic line to the wrapper console (not visible in-game). */
    private void diag(String message) {
        controller.logExternal(Level.INFO, Category.SYSTEM, "[Git] " + message);
    }

    /** Broadcast a coloured message to every player via {@code tellraw @a}. */
    private void broadcast(String text, String color) {
        String json = "{\"text\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"")
                + "\",\"color\":\"" + color + "\"}";
        controller.sendBackground("tellraw @a " + json);
    }
}
