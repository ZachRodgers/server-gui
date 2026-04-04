package com.zach.minecraft.servergui.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.swing.JPanel;

public final class ChartPanel extends JPanel {
    private static final int MAX_POINTS = 48;

    private final Deque<Double> values = new ArrayDeque<>();
    private final String title;
    private final String suffix;
    private final Color lineColor;
    private final DecimalFormat format = new DecimalFormat("0.00");

    private double maxValue = 1.0;
    private double latestValue;

    public ChartPanel(String title, String suffix, Color lineColor) {
        this.title = title;
        this.suffix = suffix;
        this.lineColor = lineColor;
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

        int scale = 2;
        MinecraftFont.drawString(g2, title.toUpperCase(), 8, 6, scale, MinecraftTheme.PANEL_TEXT, true);
        String value = format.format(latestValue) + suffix;
        int valueWidth = MinecraftFont.textWidth(value, scale);
        MinecraftFont.drawString(g2, value, getWidth() - valueWidth - 8, 6, scale, lineColor, true);

        int left = 10;
        int right = getWidth() - 10;
        int top = 28;
        int bottom = getHeight() - 10;

        g2.setColor(MinecraftTheme.TEXT_DARK);
        g2.setStroke(new BasicStroke(1f));
        for (int i = 0; i <= 3; i++) {
            int y = top + i * (bottom - top) / 3;
            g2.drawLine(left, y, right, y);
        }

        if (values.size() > 1) {
            Path2D fill = new Path2D.Double();
            Path2D line = new Path2D.Double();
            int index = 0;
            int span = Math.max(1, values.size() - 1);
            double startX = left;

            for (double point : values) {
                double x = left + (double) (right - left) * index / span;
                double normalized = Math.min(1.0, point / maxValue);
                double y = bottom - normalized * (bottom - top);
                if (index == 0) {
                    fill.moveTo(x, y);
                    line.moveTo(x, y);
                    startX = x;
                } else {
                    fill.lineTo(x, y);
                    line.lineTo(x, y);
                }
                if (index == values.size() - 1) {
                    fill.lineTo(x, bottom);
                    fill.lineTo(startX, bottom);
                    fill.closePath();
                }
                index++;
            }

            g2.setColor(new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 70));
            g2.fill(fill);
            g2.setColor(lineColor);
            g2.setStroke(new BasicStroke(2f));
            g2.draw(line);
        }
        g2.dispose();
    }
}
