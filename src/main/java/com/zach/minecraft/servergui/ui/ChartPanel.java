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
    private static final Color BG        = new Color(0x18181B);  // zinc-900 card
    private static final Color BORDER_COL = new Color(0x27272A); // zinc-800
    private static final Color GRID_COL  = new Color(0x0F0F11);  // subtler than card
    private static final Color TITLE_COL = new Color(0xA1A1AA);  // zinc-400

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
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        g2.setColor(BG);
        g2.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);
        g2.setColor(BORDER_COL);
        g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);

        g2.setColor(TITLE_COL);
        g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
        g2.drawString(title, 14, 22);

        String val = format.format(latestValue) + suffix;
        g2.setColor(lineColor);
        int valWidth = g2.getFontMetrics().stringWidth(val);
        g2.drawString(val, w - valWidth - 14, 22);

        int left = 14, right = w - 14, top = 34, bottom = h - 12;

        g2.setColor(GRID_COL);
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

            for (double value : values) {
                double x = left + (double) (right - left) * index / span;
                double normalized = Math.min(1.0, value / maxValue);
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

            g2.setColor(new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 35));
            g2.fill(fill);
            g2.setColor(lineColor);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(line);
        }

        g2.dispose();
    }
}
