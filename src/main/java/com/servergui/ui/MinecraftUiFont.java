package com.servergui.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

final class MinecraftUiFont {
    private static final Font BASE_FONT = loadBaseFont();
    private static final FontRenderContext FONT_RENDER_CONTEXT = new FontRenderContext(null, false, false);
    private static final Map<Integer, String> SMALL_CAPS = Map.ofEntries(
            Map.entry((int) 'a', "\u1d00"),
            Map.entry((int) 'b', "\u0299"),
            Map.entry((int) 'c', "\u1d04"),
            Map.entry((int) 'd', "\u1d05"),
            Map.entry((int) 'e', "\u1d07"),
            Map.entry((int) 'f', "\ua730"),
            Map.entry((int) 'g', "\u0262"),
            Map.entry((int) 'h', "\u029c"),
            Map.entry((int) 'i', "\u026a"),
            Map.entry((int) 'j', "\u1d0a"),
            Map.entry((int) 'k', "\u1d0b"),
            Map.entry((int) 'l', "\u029f"),
            Map.entry((int) 'm', "\u1d0d"),
            Map.entry((int) 'n', "\u0274"),
            Map.entry((int) 'o', "\u1d0f"),
            Map.entry((int) 'p', "\u1d18"),
            Map.entry((int) 'q', "\u01eb"),
            Map.entry((int) 'r', "\u0280"),
            Map.entry((int) 's', "\ua731"),
            Map.entry((int) 't', "\u1d1b"),
            Map.entry((int) 'u', "\u1d1c"),
            Map.entry((int) 'v', "\u1d20"),
            Map.entry((int) 'w', "\u1d21"),
            Map.entry((int) 'x', "x"),
            Map.entry((int) 'y', "\u028f"),
            Map.entry((int) 'z', "\u1d22")
    );

    private MinecraftUiFont() {}

    static Font font(float size) {
        return BASE_FONT.deriveFont(size);
    }

    static float scaledSize(int scale) {
        return (float) (scale * 8f * MinecraftTheme.uiScale());
    }

    static int textWidth(Graphics2D g2, String text, float size) {
        g2.setFont(font(size));
        return textWidth(g2.getFontMetrics(), text);
    }

    static int textWidth(String text, float size) {
        return textWidth(font(size), text);
    }

    static int lineHeight(Graphics2D g2, float size) {
        g2.setFont(font(size));
        return g2.getFontMetrics().getHeight();
    }

    static int lineHeight(float size) {
        return (int) Math.ceil(font(size).getLineMetrics("Ag", FONT_RENDER_CONTEXT).getHeight());
    }

    static int ascent(float size) {
        return (int) Math.ceil(font(size).getLineMetrics("Ag", FONT_RENDER_CONTEXT).getAscent());
    }

    static String toSmallCaps(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder builder = new StringBuilder(text.length());
        String lower = text.toLowerCase();
        for (int i = 0; i < lower.length(); ) {
            int codePoint = lower.codePointAt(i);
            builder.append(SMALL_CAPS.getOrDefault(codePoint, new String(Character.toChars(codePoint))));
            i += Character.charCount(codePoint);
        }
        return builder.toString();
    }

    static void draw(Graphics2D g2, String text, int x, int baselineY, float size, Color color, boolean shadow) {
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setFont(font(size));
        var metrics = g2.getFontMetrics();
        if (shadow) {
            g2.setColor(MinecraftTheme.TEXT_DARK);
            drawRuns(g2, metrics, text, x + 1, baselineY + 1);
        }
        g2.setColor(color);
        drawRuns(g2, metrics, text, x, baselineY);
    }

    private static int textWidth(Font font, String text) {
        return textWidth(new java.awt.Canvas().getFontMetrics(font), text);
    }

    private static int textWidth(java.awt.FontMetrics metrics, String text) {
        if (text == null || text.isEmpty()) return 0;
        int width = 0;
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                if (!run.isEmpty()) {
                    width += metrics.stringWidth(run.toString());
                    run.setLength(0);
                }
                width += switch (ch) {
                    case '\t' -> spaceWidth(metrics) * 4;
                    case '\n', '\r' -> 0;
                    default -> spaceWidth(metrics);
                };
            } else {
                run.append(ch);
            }
        }
        if (!run.isEmpty()) width += metrics.stringWidth(run.toString());
        return width;
    }

    private static void drawRuns(Graphics2D g2, java.awt.FontMetrics metrics, String text, int x, int baselineY) {
        if (text == null || text.isEmpty()) return;
        int cursor = x;
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                if (!run.isEmpty()) {
                    String value = run.toString();
                    g2.drawString(value, cursor, baselineY);
                    cursor += metrics.stringWidth(value);
                    run.setLength(0);
                }
                cursor += switch (ch) {
                    case '\t' -> spaceWidth(metrics) * 4;
                    case '\n', '\r' -> 0;
                    default -> spaceWidth(metrics);
                };
            } else {
                run.append(ch);
            }
        }
        if (!run.isEmpty()) g2.drawString(run.toString(), cursor, baselineY);
    }

    private static int spaceWidth(java.awt.FontMetrics metrics) {
        return Math.max(6, metrics.stringWidth("i"));
    }

    private static Font loadBaseFont() {
        try (InputStream input = MinecraftUiFont.class.getClassLoader().getResourceAsStream("fonts/minecraft-ui.otf")) {
            if (input != null) {
                Font font = Font.createFont(Font.TRUETYPE_FONT, input);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
                return font.deriveFont(Font.PLAIN, 12f);
            }
        } catch (IOException | FontFormatException ignored) {
        }

        GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        List<String> available = Arrays.asList(environment.getAvailableFontFamilyNames());
        List<String> preferredFamilies = List.of(
                "Noto Sans Mono",
                "DejaVu Sans Mono",
                "Liberation Mono",
                Font.MONOSPACED,
                Font.DIALOG
        );
        for (String family : preferredFamilies) {
            if (available.contains(family) || Font.MONOSPACED.equals(family) || Font.DIALOG.equals(family)) {
                Font font = new Font(family, Font.PLAIN, 12);
                return font;
            }
        }
        return new Font(available.isEmpty() ? Font.DIALOG : available.get(0), Font.PLAIN, 12);
    }
}
