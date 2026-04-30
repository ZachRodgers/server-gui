package com.servergui.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Loads, edits, and writes a Minecraft {@code server.properties} file while
 * preserving existing comments and key order.
 */
public final class ServerProperties {
    private final Path path;
    private final List<String> originalLines;
    private final LinkedHashMap<String, String> values;

    private ServerProperties(Path path, List<String> originalLines, LinkedHashMap<String, String> values) {
        this.path = path;
        this.originalLines = originalLines;
        this.values = values;
    }

    public static ServerProperties load(Path path) {
        List<String> lines = new ArrayList<>();
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        if (Files.exists(path)) {
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) continue;
                    int eq = trimmed.indexOf('=');
                    if (eq < 0) continue;
                    String key = trimmed.substring(0, eq).trim();
                    String value = trimmed.substring(eq + 1);
                    values.put(key, value);
                }
            } catch (IOException ignored) {
            }
        }
        return new ServerProperties(path, lines, values);
    }

    /** Reads only the {@code motd} value from a file without holding it open. */
    public static Optional<String> readMotd(Path path) {
        if (!Files.exists(path)) return Optional.empty();
        Properties props = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException ex) {
            return Optional.empty();
        }
        String motd = props.getProperty("motd");
        return motd == null || motd.isBlank() ? Optional.empty() : Optional.of(motd);
    }

    public Path path() {
        return path;
    }

    public boolean exists() {
        return Files.exists(path);
    }

    public String get(String key, String defaultValue) {
        return values.getOrDefault(key, defaultValue);
    }

    public Optional<String> getOptional(String key) {
        return Optional.ofNullable(values.get(key));
    }

    public boolean contains(String key) {
        return values.containsKey(key);
    }

    public Map<String, String> all() {
        return new LinkedHashMap<>(values);
    }

    public void set(String key, String value) {
        values.put(key, value == null ? "" : value);
    }

    public void save() throws IOException {
        List<String> output = new ArrayList<>();
        java.util.Set<String> writtenKeys = new java.util.HashSet<>();
        for (String line : originalLines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                output.add(line);
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq < 0) {
                output.add(line);
                continue;
            }
            String key = trimmed.substring(0, eq).trim();
            if (values.containsKey(key)) {
                output.add(key + "=" + values.get(key));
                writtenKeys.add(key);
            } else {
                output.add(line);
            }
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!writtenKeys.contains(entry.getKey())) {
                output.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (String line : output) {
                writer.write(line);
                writer.newLine();
            }
        }
        originalLines.clear();
        originalLines.addAll(output);
    }

    /** Strip Minecraft section/ampersand color codes and unescape literal "\n". */
    public static String stripColorCodes(String motd) {
        if (motd == null) return "";
        String cleaned = motd.replaceAll("(?i)[§&][0-9a-fk-or]", "");
        cleaned = cleaned.replace("\\n", " ").replace("\n", " ");
        return cleaned.trim();
    }
}
