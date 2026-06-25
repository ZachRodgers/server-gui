package com.servergui;

import com.servergui.config.AppConfig;
import com.servergui.service.GitSyncService;
import com.servergui.service.ServerController;
import com.servergui.service.ServerController.ServerStatus;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Headless runner for the {@code -nogui} flag. Launches the server, streams its output to
 * the terminal, and forwards typed input to the server, behaving like running the server jar
 * directly. The only extra behavior is the background {@code !git} chat listener; the GUI,
 * polling, and player views are skipped.
 */
public final class HeadlessRunner {

    private final AppConfig config;

    public HeadlessRunner(AppConfig config) {
        this.config = config;
    }

    public void run() {
        ServerController controller = new ServerController(config);
        controller.setPollingEnabled(false); // git-only: no list/tps/heap injection
        GitSyncService gitSync = new GitSyncService(controller);
        gitSync.reconfigure(config);

        // If our process is killed, take the server child down with us (no orphans).
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            gitSync.shutdown();
            controller.destroyProcess();
        }, "headless-shutdown"));

        controller.onLog(entry -> System.out.println(entry.message()));
        controller.onChat(message -> gitSync.handleChat(message.player(), message.text()));
        controller.onStatus(status -> {
            if (status == ServerStatus.OFFLINE) System.exit(0);
        });

        controller.start();

        // Forward terminal input straight to the server, no echo (matches a normal console).
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String command = line.strip();
                if (!command.isEmpty()) controller.sendBackground(command);
            }
        } catch (IOException ignored) {
            // stdin closed: keep streaming server output until the server itself exits.
        }
    }
}
