package com.zach.minecraft.servergui.ui;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.swing.JPanel;

public final class ChartPanel extends JPanel {
    private static final int MAX_POINTS = 48;

    private final Deque<Double> values = new ArrayDeque<>();
    private final String title;
    private final DecimalFormat format = new DecimalFormat("0.00");

    private double maxValue = 1.0;
    private double latestValue;

    public ChartPanel(String title, String suffix, Color lineColor) {
        this.title = title;
        setOpaque(false);
    }

    public void addPoint(double value, double suggestedMax) {
        latestValue = value;
        maxValue = Math.max(1.0, suggestedMax);
        if (values.size() == MAX_POINTS) values.removeFirst();
        values.addLast(value);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics.create();
        MinecraftTheme.paintTiledBackground(g2, MinecraftTheme.LIST_BG, 0, 0, getWidth(), getHeight(), 0.95f);

        float fontSize = MinecraftUiFont.scaledSize(2);
        FontMetrics metrics = g2.getFontMetrics(MinecraftUiFont.font(fontSize));
        int baseline = MinecraftTheme.scale(8) + metrics.getAscent();
        MinecraftUiFont.draw(g2, MinecraftUiFont.toSmallCaps(title), 8, baseline, fontSize, MinecraftTheme.PANEL_TEXT, true);
        Color currentColor = currentColor();
        String value = displayValue();
        int valueWidth = MinecraftUiFont.textWidth(value, fontSize);
        MinecraftUiFont.draw(g2, value, getWidth() - valueWidth - 8, baseline, fontSize, currentColor, true);

        int left = MinecraftTheme.scale(10);
        int right = getWidth() - MinecraftTheme.scale(10);
        int top = MinecraftTheme.scale(28);
        int bottom = getHeight() - MinecraftTheme.scale(10);

        g2.setColor(MinecraftTheme.TEXT_DARK);
        for (int i = 0; i <= 3; i++) {
            int y = top + i * (bottom - top) / 3;
            g2.drawLine(left, y, right, y);
        }

        if (values.size() > 1) {
            int index = 0;
            int span = Math.max(1, values.size() - 1);
            int previousX = left;
            int previousY = bottom;
            boolean first = true;

            for (double point : values) {
                int x = left + (right - left) * index / span;
                double normalized = Math.min(1.0, point / maxValue);
                int y = bottom - (int) Math.round(normalized * (bottom - top));

                if (!first) {
                    int width = Math.max(1, x - previousX);
                    int fillTop = Math.min(previousY, bottom);
                    g2.setColor(new Color(currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue(), 55));
                    g2.fillRect(previousX, fillTop, width, bottom - fillTop);

                    g2.setColor(currentColor);
                    int stroke = Math.max(2, MinecraftTheme.scale(2));
                    g2.fillRect(previousX, previousY - (stroke / 2), width, stroke);
                    g2.fillRect(x - (stroke / 2), Math.min(previousY, y), stroke, Math.abs(y - previousY) + 1);
                }

                previousX = x;
                previousY = y;
                first = false;
                index++;
            }
            g2.setColor(currentColor);
            int point = Math.max(2, MinecraftTheme.scale(2));
            g2.fillRect(previousX - (point / 2), previousY - (point / 2), point, point);
        }
        g2.dispose();
    }

    private String displayValue() {
        if ("Memory".equalsIgnoreCase(title)) {
            return (int) Math.round(latestValue) + "/" + (int) Math.round(maxValue) + " MB";
        }
        return format.format(latestValue);
    }

    private Color currentColor() {
        if ("Memory".equalsIgnoreCase(title)) {
            double ratio = maxValue <= 0 ? 0 : latestValue / maxValue;
            if (ratio >= 0.8) return MinecraftTheme.ERROR;
            if (ratio >= 0.5) return MinecraftTheme.WARN;
            return MinecraftTheme.SUCCESS;
        }
        if (latestValue < 15.0) return MinecraftTheme.ERROR;
        if (latestValue < 19.0) return MinecraftTheme.WARN;
        return MinecraftTheme.SUCCESS;
    }
}
