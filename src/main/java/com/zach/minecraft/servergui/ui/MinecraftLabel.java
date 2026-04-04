package com.zach.minecraft.servergui.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.JComponent;

final class MinecraftLabel extends JComponent {
    private String text;
    private Color color = MinecraftTheme.PANEL_TEXT;
    private int scale;
    private boolean shadow = true;

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

    @Override
    public Dimension getPreferredSize() {
        int width = MinecraftFont.textWidth(text == null ? "" : text, scale);
        return new Dimension(Math.max(1, width), MinecraftFont.lineHeight(scale));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        MinecraftFont.drawString((java.awt.Graphics2D) graphics, text == null ? "" : text, 0, 0, scale, color, shadow);
    }
}
