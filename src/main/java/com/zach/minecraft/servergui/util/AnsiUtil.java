package com.zach.minecraft.servergui.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utilities for handling ANSI escape sequences from Minecraft server output. */
public final class AnsiUtil {
    private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B?\\[([0-9;]*)m");

    // Standard 16-colour palette (indices 0-7 = codes 30-37, 8-15 = codes 90-97)
    private static final String[] ANSI_HEX = {
        "1A1A1A", // 30 black (shifted lighter so it's visible on dark bg)
        "CC3333", // 31 red
        "33AA44", // 32 green
        "FFAA00", // 33 yellow/orange
        "4488FF", // 34 blue
        "CC44CC", // 35 magenta
        "33BBBB", // 36 cyan
        "FAFAFA", // 37 light gray -> match light UI foreground
        "555555", // 90 dark gray
        "FF5555", // 91 bright red
        "55FF55", // 92 bright green
        "FFFF55", // 93 bright yellow
        "5599FF", // 94 bright blue
        "FF55FF", // 95 bright magenta
        "55FFFF", // 96 bright cyan
        "FFFFFF", // 97 white
    };

    private AnsiUtil() {}

    /** Strip all ANSI escape sequences, returning plain text. */
    public static String strip(String input) {
        if (input == null) return "";
        return ANSI_PATTERN.matcher(input).replaceAll("");
    }

    /**
     * Convert a string with ANSI colour codes to an HTML snippet for rendering
     * in a JLabel/table cell (dark background assumed).
     * Handles both RGB (38;2;R;G;B) and standard 16-colour codes (30-37, 90-97).
     */
    public static String toHtml(String input) {
        if (input == null) return "";
        if (!input.contains("[")) return "<html><body style='font-family:monospace;margin:0;padding:0;color:#FAFAFA;'>"
                + escapeHtml(input) + "</body></html>";

        StringBuilder sb = new StringBuilder(
                "<html><body style='font-family:monospace;margin:0;padding:0;color:#FAFAFA;'>");
        Matcher m = ANSI_PATTERN.matcher(input);
        int last = 0;
        boolean inSpan = false;

        while (m.find()) {
            String text = input.substring(last, m.start());
            if (!text.isEmpty()) sb.append(escapeHtml(text));
            last = m.end();

            String codes = m.group(1);
            if (codes.isEmpty() || "0".equals(codes)) {
                if (inSpan) { sb.append("</span>"); inSpan = false; }
            } else if (codes.startsWith("38;2;")) {
                // True-colour RGB
                String[] parts = codes.split(";");
                if (parts.length >= 5) {
                    if (inSpan) sb.append("</span>");
                    sb.append(String.format("<span style='color:rgb(%s,%s,%s);'>",
                            parts[2], parts[3], parts[4]));
                    inSpan = true;
                }
            } else {
                // Standard 16-colour
                try {
                    int code = Integer.parseInt(codes);
                    String hex = null;
                    if (code >= 30 && code <= 37) hex = ANSI_HEX[code - 30];
                    else if (code >= 90 && code <= 97) hex = ANSI_HEX[code - 82];
                    if (hex != null) {
                        if (inSpan) sb.append("</span>");
                        sb.append("<span style='color:#").append(hex).append(";'>");
                        inSpan = true;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        String tail = input.substring(last);
        if (!tail.isEmpty()) sb.append(escapeHtml(tail));
        if (inSpan) sb.append("</span>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
