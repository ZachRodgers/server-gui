package com.servergui.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;

final class MinecraftLabel extends JComponent {
    private String text;
    private Color color = MinecraftTheme.PANEL_TEXT;
    private int scale;
    private boolean shadow = true;
    private boolean smallCaps;

    MinecraftLabel(String text, int scale) {
        this.text = text;
        this.scale = scale;
        setOpaque(false);
    }

    void setPixelText(String text) {
        this.text = text;
        revalidate();
        repaint();
    }

    void setPixelColor(Color color) {
        this.color = color;
        repaint();
    }

    void setScale(int scale) {
        this.scale = scale;
        revalidate();
        repaint();
    }

    void setShadow(boolean shadow) {
        this.shadow = shadow;
        repaint();
    }

    void setSmallCaps(boolean smallCaps) {
        this.smallCaps = smallCaps;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        float fontSize = MinecraftUiFont.scaledSize(scale);
        int width = MinecraftUiFont.textWidth(displayText(), fontSize);
        return new Dimension(Math.max(1, width), MinecraftUiFont.lineHeight(fontSize));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics.create();
        float fontSize = MinecraftUiFont.scaledSize(scale);
        FontMetrics metrics = g2.getFontMetrics(MinecraftUiFont.font(fontSize));
        int baseline = Math.max(metrics.getAscent(), (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
        MinecraftUiFont.draw(g2, displayText(), 0, baseline, fontSize, color, shadow);
        g2.dispose();
    }

    private String displayText() {
        String value = text == null ? "" : text;
        return smallCaps ? MinecraftUiFont.toSmallCaps(value) : value;
    }
}
