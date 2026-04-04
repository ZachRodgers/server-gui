package com.zach.minecraft.servergui.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
        if (values.size() == MAX_POINTS) {
            values.removeFirst();
        }
        values.addLast(value);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        g2.setColor(new Color(0xF6F8FB));
        g2.fillRoundRect(0, 0, width - 1, height - 1, 24, 24);
        g2.setColor(new Color(0xD9E0EA));
        g2.drawRoundRect(0, 0, width - 1, height - 1, 24, 24);

        g2.setColor(new Color(0x243447));
        g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
        g2.drawString(title, 16, 24);

        g2.setColor(new Color(0x5B6777));
        g2.setFont(getFont().deriveFont(Font.PLAIN, 12f));
        g2.drawString(format.format(latestValue) + suffix, width - 110, 24);

        int left = 16;
        int right = width - 16;
        int top = 36;
        int bottom = height - 18;

        g2.setColor(new Color(0xE4EAF2));
        for (int i = 0; i < 4; i++) {
            int y = top + i * (bottom - top) / 3;
            g2.drawLine(left, y, right, y);
        }

        if (values.size() > 1) {
            Path2D path = new Path2D.Double();
            int index = 0;
            int span = Math.max(1, values.size() - 1);
            for (double value : values) {
                double x = left + (double) (right - left) * index / span;
                double normalized = Math.min(1.0, value / maxValue);
                double y = bottom - normalized * (bottom - top);
                if (index == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
                index++;
            }
            g2.setColor(lineColor);
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(path);
        }

        g2.dispose();
    }
}
