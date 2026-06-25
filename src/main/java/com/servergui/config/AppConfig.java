package com.servergui.config;

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
import java.util.regex.Pattern;
import java.util.stream.Stream;

public record AppConfig(
        Path baseDirectory,
        String appTitle,
        boolean mockMode,
        String serverCommand,
        Path workingDirectory,
        int playerPollSeconds,
        int tpsPollSeconds,
        int heapPollSeconds,
        boolean gitSyncEnabled,
        String gitRepository
) {
    private static final String FILE_NAME = "server-wrapper.properties";

    public static AppConfig load(Path baseDirectory) {
        Path normalizedBaseDirectory = baseDirectory.toAbsolutePath().normalize();
        Path configPath = normalizedBaseDirectory.resolve(FILE_NAME);
        if (Files.notExists(configPath)) {
            writeDefault(configPath);
        }
        ensureLauncherScripts(normalizedBaseDirectory);

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(configPath)) {
            properties.load(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load " + configPath, exception);
        }

        String appTitle = properties.getProperty("app.title", "Minecraft Server Console");
        boolean mockMode = Boolean.parseBoolean(properties.getProperty("mock.mode", "true"));
        String serverCommand = properties.getProperty("server.command", "java -Xms3G -Xmx6G -jar server.jar nogui");
        Path workingDirectory = normalizedBaseDirectory.resolve(properties.getProperty("working.directory", ".")).normalize();
        int playerPollSeconds = Integer.parseInt(properties.getProperty("poll.players.seconds", "30"));
        int tpsPollSeconds    = Integer.parseInt(properties.getProperty("poll.tps.seconds",    "30"));
        int heapPollSeconds   = Integer.parseInt(properties.getProperty("poll.heap.seconds",   "20"));
        boolean gitSyncEnabled = Boolean.parseBoolean(properties.getProperty("git.sync.enabled", "false"));
        String gitRepository  = properties.getProperty("git.repository", "");

        if (!mockMode) {
            serverCommand = reconcileServerJar(workingDirectory, configPath, serverCommand);
        }

        return new AppConfig(normalizedBaseDirectory, appTitle, mockMode, serverCommand, workingDirectory,
                playerPollSeconds, tpsPollSeconds, heapPollSeconds, gitSyncEnabled, gitRepository);
    }

    /** Absolute path to the git repository directory, or empty when none is configured. */
    public Optional<Path> gitRepositoryPath() {
        if (gitRepository == null || gitRepository.isBlank()) return Optional.empty();
        return Optional.of(baseDirectory.resolve(gitRepository).normalize());
    }

    public List<String> commandTokens() {
        return CommandTokenizer.tokenize(serverCommand);
    }

    public Path configPath() {
        return baseDirectory.resolve(FILE_NAME);
    }

    public String minHeap() {
        return extractJvmFlag(serverCommand, "-Xms").orElse("3G");
    }

    public String maxHeap() {
        return extractJvmFlag(serverCommand, "-Xmx").orElse("6G");
    }

    public AppConfig save(String appTitle, int playerPollSeconds, int tpsPollSeconds, int heapPollSeconds, String minHeap, String maxHeap) {
        Properties properties = new Properties();
        Path configPath = configPath();
        if (Files.exists(configPath)) {
            try (InputStream inputStream = Files.newInputStream(configPath)) {
                properties.load(inputStream);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to load " + configPath, exception);
            }
        }

        String updatedCommand = updateJvmFlag(updateJvmFlag(serverCommand, "-Xms", minHeap), "-Xmx", maxHeap);
        properties.setProperty("app.title", appTitle);
        properties.setProperty("mock.mode", String.valueOf(mockMode));
        properties.setProperty("server.command", updatedCommand);
        properties.setProperty("working.directory", relativizeWorkingDirectory());
        properties.setProperty("poll.players.seconds", String.valueOf(playerPollSeconds));
        properties.setProperty("poll.tps.seconds", String.valueOf(tpsPollSeconds));
        properties.setProperty("poll.heap.seconds", String.valueOf(heapPollSeconds));

        try (OutputStream outputStream = Files.newOutputStream(configPath)) {
            properties.store(outputStream, "Minecraft server GUI configuration.");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save " + configPath, exception);
        }

        return new AppConfig(baseDirectory, appTitle, mockMode, updatedCommand, workingDirectory,
                playerPollSeconds, tpsPollSeconds, heapPollSeconds, gitSyncEnabled, gitRepository);
    }

    /** Persist only the git-sync settings, preserving every other key. */
    public AppConfig saveGitSync(boolean enabled, Path repository) {
        Properties properties = new Properties();
        Path configPath = configPath();
        if (Files.exists(configPath)) {
            try (InputStream inputStream = Files.newInputStream(configPath)) {
                properties.load(inputStream);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to load " + configPath, exception);
            }
        }

        String relativeRepository = relativizePath(repository);
        properties.setProperty("git.sync.enabled", String.valueOf(enabled));
        properties.setProperty("git.repository", relativeRepository);

        try (OutputStream outputStream = Files.newOutputStream(configPath)) {
            properties.store(outputStream, "Minecraft server GUI configuration.");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save " + configPath, exception);
        }

        return new AppConfig(baseDirectory, appTitle, mockMode, serverCommand, workingDirectory,
                playerPollSeconds, tpsPollSeconds, heapPollSeconds, enabled, relativeRepository);
    }

    private String relativizePath(Path target) {
        if (target == null) return "";
        Path normalized = target.toAbsolutePath().normalize();
        try {
            Path relative = baseDirectory.relativize(normalized);
            return relative.toString().isBlank() ? "." : relative.toString();
        } catch (IllegalArgumentException ignored) {
            return normalized.toString();
        }
    }

    private String relativizeWorkingDirectory() {
        try {
            Path relative = baseDirectory.relativize(workingDirectory);
            return relative.toString().isBlank() ? "." : relative.toString();
        } catch (IllegalArgumentException ignored) {
            return workingDirectory.toString();
        }
    }

    private static Optional<String> extractJvmFlag(String command, String flagName) {
        return CommandTokenizer.tokenize(command).stream()
                .filter(token -> token.startsWith(flagName))
                .map(token -> token.substring(flagName.length()))
                .filter(value -> !value.isBlank())
                .findFirst();
    }

    private static String updateJvmFlag(String command, String flagName, String value) {
        String normalizedValue = value == null ? "" : value.trim();
        if (normalizedValue.isBlank()) return command;

        List<String> tokens = CommandTokenizer.tokenize(command);
        boolean replaced = false;
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).startsWith(flagName)) {
                tokens.set(i, flagName + normalizedValue);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            int insertAt = !tokens.isEmpty() && tokens.get(0).toLowerCase(Locale.ROOT).contains("java") ? 1 : 0;
            tokens.add(insertAt, flagName + normalizedValue);
        }
        return joinTokens(tokens);
    }

    private static String joinTokens(List<String> tokens) {
        return tokens.stream()
                .map(token -> Pattern.compile("\\s").matcher(token).find() ? "\"" + token.replace("\"", "\\\"") + "\"" : token)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
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
        defaults.setProperty("poll.players.seconds", "30");
        defaults.setProperty("poll.tps.seconds",    "30");
        defaults.setProperty("poll.heap.seconds",   "20");

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

    /**
     * If the jar named in {@code server.command} is missing from the working directory,
     * find an updated replacement (same name family preferred, newest version) and rewrite
     * the command in {@code server-wrapper.properties}. Returns the command to use.
     */
    private static String reconcileServerJar(Path workingDirectory, Path configPath, String serverCommand) {
        Optional<String> configured = jarFromCommand(serverCommand);
        if (configured.isEmpty()) return serverCommand;
        String jarName = configured.get();
        if (Files.exists(workingDirectory.resolve(jarName))) return serverCommand;

        Optional<String> replacement = findReplacementJar(workingDirectory, jarName);
        if (replacement.isEmpty() || replacement.get().equals(jarName)) return serverCommand;

        String updated = replaceJarToken(serverCommand, jarName, replacement.get());
        persistServerCommand(configPath, updated);
        return updated;
    }

    private static Optional<String> jarFromCommand(String command) {
        List<String> tokens = CommandTokenizer.tokenize(command);
        for (int i = 0; i < tokens.size() - 1; i++) {
            if (tokens.get(i).equals("-jar")) return Optional.of(tokens.get(i + 1));
        }
        return Optional.empty();
    }

    private static Optional<String> findReplacementJar(Path directory, String missingJar) {
        String family = jarFamily(missingJar);
        if (!family.isBlank()) {
            try (Stream<Path> files = Files.list(directory)) {
                Optional<String> sameFamily = files
                        .filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".jar"))
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(family))
                        .max(AppConfig::compareNatural);
                if (sameFamily.isPresent()) return sameFamily;
            } catch (IOException ignored) {
                return Optional.empty();
            }
        }
        return detectServerJar(directory);
    }

    /** Leading run of letters in a jar name, e.g. {@code paper} from {@code paper-1.21.10-130.jar}. */
    private static String jarFamily(String jarName) {
        String lower = jarName.toLowerCase(Locale.ROOT);
        int i = 0;
        while (i < lower.length() && Character.isLetter(lower.charAt(i))) i++;
        return lower.substring(0, i);
    }

    /** Natural ("version-aware") comparison so e.g. paper-1.21.10 sorts above paper-1.21.9. */
    private static int compareNatural(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i;
                int sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                String na = a.substring(si, i).replaceFirst("^0+(?=\\d)", "");
                String nb = b.substring(sj, j).replaceFirst("^0+(?=\\d)", "");
                if (na.length() != nb.length()) return na.length() - nb.length();
                int cmp = na.compareTo(nb);
                if (cmp != 0) return cmp;
            } else {
                int cmp = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
                if (cmp != 0) return cmp;
                i++;
                j++;
            }
        }
        return (a.length() - i) - (b.length() - j);
    }

    private static String replaceJarToken(String command, String oldJar, String newJar) {
        List<String> tokens = CommandTokenizer.tokenize(command);
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).equals(oldJar)) {
                tokens.set(i, newJar);
                break;
            }
        }
        return joinTokens(tokens);
    }

    private static void persistServerCommand(Path configPath, String command) {
        Properties properties = new Properties();
        if (Files.exists(configPath)) {
            try (InputStream inputStream = Files.newInputStream(configPath)) {
                properties.load(inputStream);
            } catch (IOException ignored) {
                return;
            }
        }
        properties.setProperty("server.command", command);
        try (OutputStream outputStream = Files.newOutputStream(configPath)) {
            properties.store(outputStream, "Minecraft server GUI configuration.");
        } catch (IOException ignored) {
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

    private static void ensureLauncherScripts(Path baseDirectory) {
        writeLauncher(baseDirectory.resolve("start.sh"), shellLauncher());
        writeLauncher(baseDirectory.resolve("start.bat"), windowsLauncher());
        try {
            Path shell = baseDirectory.resolve("start.sh");
            if (Files.exists(shell)) {
                shell.toFile().setExecutable(true, false);
            }
        } catch (SecurityException ignored) {
        }
    }

    private static void writeLauncher(Path path, String content) {
        try {
            Files.writeString(path, content);
        } catch (IOException ignored) {
        }
    }

    private static String shellLauncher() {
        return """
                #!/bin/bash
                cd "$(dirname "$0")"
                JAR="$(ls server-gui*.jar 2>/dev/null | sort -V | tail -n 1)"
                if [ -z "$JAR" ]; then
                  echo "server-gui jar not found."
                  exit 1
                fi
                nohup java -Dsun.awt.X11.XWMClass=com-servergui-Main -jar "$JAR" &>/dev/null &
                """;
    }

    private static String windowsLauncher() {
        return """
                @echo off
                cd /d "%~dp0"
                for /f "delims=" %%f in ('dir /b /o-n server-gui*.jar 2^>nul') do (
                  start "" javaw -jar "%%f"
                  goto :eof
                )
                echo server-gui jar not found.
                """;
    }
}
