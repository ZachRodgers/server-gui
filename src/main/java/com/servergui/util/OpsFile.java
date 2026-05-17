package com.servergui.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads operator names from a Minecraft server's {@code ops.json}. */
public final class OpsFile {

    private static final Pattern OPS_NAME_PATTERN =
            Pattern.compile("\"name\"\\s*:\\s*\"([A-Za-z0-9_]{3,16})\"");

    private OpsFile() {
    }

    /** All operator names listed in {@code <workingDirectory>/ops.json}; empty when absent or unreadable. */
    public static Set<String> readOps(Path workingDirectory) {
        Set<String> ops = new LinkedHashSet<>();
        Path opsPath = workingDirectory.resolve("ops.json");
        if (Files.notExists(opsPath)) return ops;
        try {
            String json = Files.readString(opsPath);
            Matcher matcher = OPS_NAME_PATTERN.matcher(json);
            while (matcher.find()) {
                ops.add(matcher.group(1));
            }
        } catch (IOException ignored) {
        }
        return ops;
    }

    /** True when {@code name} appears in {@code ops.json} (case-insensitive). */
    public static boolean isOpped(Path workingDirectory, String name) {
        if (name == null || name.isBlank()) return false;
        for (String op : readOps(workingDirectory)) {
            if (op.equalsIgnoreCase(name)) return true;
        }
        return false;
    }
}
