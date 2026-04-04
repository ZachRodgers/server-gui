package com.zach.minecraft.servergui.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

public record AppConfig(
        String appTitle,
        boolean mockMode,
        String serverCommand,
        Path workingDirectory,
        int playerPollSeconds,
        int tpsPollSeconds,
        int heapPollSeconds
) {
    private static final String FILE_NAME = "server-wrapper.properties";

    public static AppConfig load(Path baseDirectory) {
        Path configPath = baseDirectory.resolve(FILE_NAME);
        if (Files.notExists(configPath)) {
            writeDefault(configPath);
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(configPath)) {
            properties.load(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load " + configPath, exception);
        }

        String appTitle = properties.getProperty("app.title", "Minecraft Server Console");
        boolean mockMode = Boolean.parseBoolean(properties.getProperty("mock.mode", "true"));
        String serverCommand = properties.getProperty("server.command", "java -Xms3G -Xmx6G -jar server.jar nogui");
        Path workingDirectory = baseDirectory.resolve(properties.getProperty("working.directory", ".")).normalize();
        int playerPollSeconds = Integer.parseInt(properties.getProperty("poll.players.seconds", "10"));
        int tpsPollSeconds = Integer.parseInt(properties.getProperty("poll.tps.seconds", "15"));
        int heapPollSeconds = Integer.parseInt(properties.getProperty("poll.heap.seconds", "10"));

        return new AppConfig(appTitle, mockMode, serverCommand, workingDirectory, playerPollSeconds, tpsPollSeconds, heapPollSeconds);
    }

    public List<String> commandTokens() {
        return CommandTokenizer.tokenize(serverCommand);
    }

    private static void writeDefault(Path configPath) {
        Path baseDirectory = configPath.getParent();
        Optional<String> detectedServerJar = detectServerJar(baseDirectory);

        Properties defaults = new Properties();
        defaults.setProperty("app.title", "Minecraft Server Console");
        defaults.setProperty("mock.mode", String.valueOf(detectedServerJar.isEmpty()));
        defaults.setProperty(
                "server.command",
                detectedServerJar
                        .map(jarName -> "java -Xms3G -Xmx6G -jar \"" + jarName + "\" nogui")
                        .orElse("java -Xms3G -Xmx6G -jar server.jar nogui")
        );
        defaults.setProperty("working.directory", ".");
        defaults.setProperty("poll.players.seconds", "10");
        defaults.setProperty("poll.tps.seconds", "15");
        defaults.setProperty("poll.heap.seconds", "10");

        try (OutputStream outputStream = Files.newOutputStream(configPath)) {
            defaults.store(
                    outputStream,
                    detectedServerJar
                            .map(jarName -> "Minecraft server GUI configuration. Auto-detected server jar: " + jarName)
                            .orElse("Minecraft server GUI configuration. No server jar detected, leaving mock mode enabled.")
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create " + configPath, exception);
        }
    }

    private static Optional<String> detectServerJar(Path baseDirectory) {
        if (baseDirectory == null || Files.notExists(baseDirectory)) {
            return Optional.empty();
        }

        try (Stream<Path> files = Files.list(baseDirectory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .filter(path -> !isWrapperJar(path))
                    .sorted(Comparator.comparingInt(AppConfig::serverJarRank).thenComparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .map(path -> path.getFileName().toString())
                    .findFirst();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private static int serverJarRank(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.startsWith("paper")) {
            return 0;
        }
        if (name.startsWith("purpur")) {
            return 1;
        }
        if (name.startsWith("spigot")) {
            return 2;
        }
        if (name.startsWith("server")) {
            return 3;
        }
        if (name.contains("minecraft")) {
            return 4;
        }
        return 10;
    }

    private static boolean isWrapperJar(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.startsWith("server-gui") || name.contains("wrapper");
    }
}
