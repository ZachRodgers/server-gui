package com.zach.minecraft.servergui.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/**
 * Renders a Minecraft MOTD with §-style color codes parsed into colored runs.
 * Strips formatting codes (§k/§l/§m/§n/§o/§r) but honors the colors that come
 * before them. Newline escapes are collapsed to a single space so the title
 * stays on one row.
 */
final class MotdLabel extends JComponent {
    private final double scale;
    private List<Segment> segments = List.of();

    MotdLabel(double scale) {
        this.scale = scale;
        setOpaque(false);
    }

    void setMotd(String raw) {
        this.segments = parse(raw);
        revalidate();
        repaint();
    }

    private record Segment(String text, Color color) {}

    @Override
    public Dimension getPreferredSize() {
        float fontSize = fontSize();
        int width = 0;
        for (Segment segment : segments) {
            width += MinecraftUiFont.textWidth(segment.text(), fontSize);
        }
        return new Dimension(Math.max(1, width), MinecraftUiFont.lineHeight(fontSize));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics.create();
        float fontSize = fontSize();
        FontMetrics metrics = g2.getFontMetrics(MinecraftUiFont.font(fontSize));
        int baseline = Math.max(metrics.getAscent(), (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
        int x = 0;
        for (Segment segment : segments) {
            MinecraftUiFont.draw(g2, segment.text(), x, baseline, fontSize, segment.color(), true);
            x += MinecraftUiFont.textWidth(segment.text(), fontSize);
        }
        g2.dispose();
    }

    private float fontSize() {
        return (float) (scale * 8f * MinecraftTheme.uiScale());
    }

    private static List<Segment> parse(String raw) {
        List<Segment> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        Color current = MinecraftTheme.PANEL_TEXT;
        StringBuilder buffer = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < raw.length()) {
                char code = Character.toLowerCase(raw.charAt(i + 1));
                Color next = colorFor(code);
                boolean isFormat = "klmnor".indexOf(code) >= 0;
                if (next != null || isFormat) {
                    if (buffer.length() > 0) {
                        out.add(new Segment(buffer.toString(), current));
                        buffer.setLength(0);
                    }
                    if (code == 'r') current = MinecraftTheme.PANEL_TEXT;
                    else if (next != null) current = next;
                    i += 2;
                    continue;
                }
            }
            if (c == '\\' && i + 1 < raw.length() && raw.charAt(i + 1) == 'n') {
                buffer.append(' ');
                i += 2;
                continue;
            }
            if (c == '\n' || c == '\r') {
                buffer.append(' ');
                i++;
                continue;
            }
            buffer.append(c);
            i++;
        }
        if (buffer.length() > 0) out.add(new Segment(buffer.toString(), current));
        return out;
    }

    private static Color colorFor(char code) {
        return switch (code) {
            case '0' -> new Color(0x000000);
            case '1' -> new Color(0x0000AA);
            case '2' -> new Color(0x00AA00);
            case '3' -> new Color(0x00AAAA);
            case '4' -> new Color(0xAA0000);
            case '5' -> new Color(0xAA00AA);
            case '6' -> new Color(0xFFAA00);
            case '7' -> new Color(0xAAAAAA);
            case '8' -> new Color(0x555555);
            case '9' -> new Color(0x5555FF);
            case 'a' -> new Color(0x55FF55);
            case 'b' -> new Color(0x55FFFF);
            case 'c' -> new Color(0xFF5555);
            case 'd' -> new Color(0xFF55FF);
            case 'e' -> new Color(0xFFFF55);
            case 'f' -> new Color(0xFFFFFF);
            default -> null;
        };
    }
}
