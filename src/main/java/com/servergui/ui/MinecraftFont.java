package com.servergui.ui;

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
        if (text == null || text.isEmpty()) return 0;
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            width += glyphAdvance(codePoint, scale);
            i += Character.charCount(codePoint);
        }
        return width;
    }

    static void drawString(Graphics2D g2, String text, int x, int y, int scale, Color color, boolean shadow) {
        if (text == null || text.isEmpty()) return;
        if (shadow) drawInternal(g2, text, x + scale, y + scale, scale, MinecraftTheme.TEXT_DARK);
        drawInternal(g2, text, x, y, scale, color);
    }

    static void drawSmallCaps(Graphics2D g2, String text, int x, int y, int scale, Color color, boolean shadow) {
        drawString(g2, text, x, y, scale, color, shadow);
    }

    static int smallCapsWidth(String text, int scale) {
        return textWidth(text, scale);
    }

    private static void drawInternal(Graphics2D g2, String text, int x, int y, int scale, Color color) {
        int cursor = x;
        MinecraftTheme.applyPixelRendering(g2);
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (codePoint == '\n') {
                i += Character.charCount(codePoint);
                continue;
            }

            if (isBitmapGlyph(codePoint)) {
                int code = normalize(codePoint);
                int col = code % 16;
                int row = code / 16;
                int glyphWidth = glyphWidth(codePoint);
                BufferedImage tinted = tintedSheet(color);
                if (glyphWidth > 0) {
                    g2.drawImage(
                            tinted,
                            cursor, y, cursor + (glyphWidth * scale), y + (CELL * scale),
                            col * CELL, row * CELL, col * CELL + glyphWidth, row * CELL + CELL,
                            null
                    );
                }
            } else {
                MinecraftUiFont.draw(g2, new String(Character.toChars(codePoint)), cursor, y + (CELL * scale), scale * 8f, color, false);
            }
            cursor += glyphAdvance(codePoint, scale);
            i += Character.charCount(codePoint);
        }
    }

    private static int glyphAdvance(int codePoint, int scale) {
        if (codePoint == ' ') return 4 * scale;
        if (isBitmapGlyph(codePoint)) return Math.max(2, glyphWidth(codePoint) + 1) * scale;
        return Math.max(scale * 6, MinecraftUiFont.textWidth(new String(Character.toChars(codePoint)), scale * 8f));
    }

    private static int glyphWidth(int codePoint) {
        return WIDTH_CACHE.computeIfAbsent(normalize(codePoint), MinecraftFont::scanGlyphWidth);
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

    private static boolean isBitmapGlyph(int codePoint) {
        return codePoint >= 0 && codePoint <= 255;
    }

    private static int normalize(int codePoint) {
        return isBitmapGlyph(codePoint) ? codePoint : '?';
    }
}
