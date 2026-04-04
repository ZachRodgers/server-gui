package com.zach.minecraft.servergui.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

final class MinecraftFont {
    private static final BufferedImage SHEET = MinecraftTheme.load("assets/minecraft/textures/font/ascii.png");
    private static final int CELL = 8;
    private static final Map<Integer, Integer> WIDTH_CACHE = new HashMap<>();
    private static final Map<Integer, BufferedImage> TINT_CACHE = new HashMap<>();

    private MinecraftFont() {}

    static int lineHeight(int scale) {
        return CELL * scale;
    }

    static int textWidth(String text, int scale) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += glyphAdvance(text.charAt(i), scale);
        }
        return width;
    }

    static void drawString(Graphics2D g2, String text, int x, int y, int scale, Color color, boolean shadow) {
        if (text == null || text.isEmpty()) return;
        if (shadow) {
            drawInternal(g2, text, x + scale, y + scale, scale, MinecraftTheme.TEXT_DARK);
        }
        drawInternal(g2, text, x, y, scale, color);
    }

    private static void drawInternal(Graphics2D g2, String text, int x, int y, int scale, Color color) {
        BufferedImage tinted = tintedSheet(color);
        int cursor = x;
        MinecraftTheme.applyPixelRendering(g2);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\n') continue;
            int code = normalize(ch);
            int col = code % 16;
            int row = code / 16;
            int glyphWidth = glyphWidth(ch);
            if (glyphWidth > 0) {
                g2.drawImage(
                        tinted,
                        cursor, y, cursor + (glyphWidth * scale), y + (CELL * scale),
                        col * CELL, row * CELL, col * CELL + glyphWidth, row * CELL + CELL,
                        null
                );
            }
            cursor += glyphAdvance(ch, scale);
        }
    }

    private static int glyphAdvance(char ch, int scale) {
        if (ch == ' ') return 4 * scale;
        return Math.max(2, glyphWidth(ch) + 1) * scale;
    }

    private static int glyphWidth(char ch) {
        return WIDTH_CACHE.computeIfAbsent((int) normalize(ch), MinecraftFont::scanGlyphWidth);
    }

    private static int scanGlyphWidth(int code) {
        if (code == 32) return 3;
        int col = code % 16;
        int row = code / 16;
        int baseX = col * CELL;
        int baseY = row * CELL;
        int max = 0;
        for (int x = 0; x < CELL; x++) {
            for (int y = 0; y < CELL; y++) {
                int alpha = (SHEET.getRGB(baseX + x, baseY + y) >>> 24) & 0xFF;
                if (alpha > 0) max = Math.max(max, x + 1);
            }
        }
        return max == 0 ? 4 : max;
    }

    private static BufferedImage tintedSheet(Color color) {
        int key = color.getRGB();
        return TINT_CACHE.computeIfAbsent(key, ignored -> {
            BufferedImage tinted = new BufferedImage(SHEET.getWidth(), SHEET.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = tinted.createGraphics();
            MinecraftTheme.applyPixelRendering(g2);
            g2.drawImage(SHEET, 0, 0, null);
            g2.setComposite(AlphaComposite.SrcAtop);
            g2.setColor(color);
            g2.fillRect(0, 0, tinted.getWidth(), tinted.getHeight());
            g2.dispose();
            return tinted;
        });
    }

    private static int normalize(char ch) {
        if (ch >= 0 && ch <= 255) return ch;
        return '?';
    }
}
