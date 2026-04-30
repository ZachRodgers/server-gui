package com.servergui.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JCheckBoxMenuItem;

final class MinecraftCheckBoxMenuItem extends JCheckBoxMenuItem {
    private final Color textColor;

    MinecraftCheckBoxMenuItem(String text, boolean selected, Color textColor) {
        super(text, selected);
        this.textColor = textColor;
        setOpaque(false);
        setArmed(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override
    public Dimension getPreferredSize() {
        float fontSize = MinecraftUiFont.scaledSize(2);
        int width = MinecraftUiFont.textWidth(MinecraftUiFont.toSmallCaps(getText()), fontSize) + MinecraftTheme.scale(34);
        int height = Math.max(MinecraftTheme.scale(24), MinecraftUiFont.lineHeight(fontSize) + MinecraftTheme.scale(8));
        return new Dimension(width, height);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setColor(getModel().isArmed() || getModel().isRollover() ? MinecraftTheme.SELECTION : MinecraftTheme.BG);
        g2.fillRect(0, 0, getWidth(), getHeight());

        int box = MinecraftTheme.scale(10);
        int boxX = MinecraftTheme.scale(8);
        int boxY = (getHeight() - box) / 2;
        g2.setColor(MinecraftTheme.TEXT_DARK);
        g2.drawRect(boxX, boxY, box, box);
        if (isSelected()) {
            g2.setColor(textColor);
            int inset = Math.max(2, MinecraftTheme.scale(2));
            g2.fillRect(boxX + inset, boxY + inset, box - (inset * 2) + 1, box - (inset * 2) + 1);
        }

        float fontSize = MinecraftUiFont.scaledSize(2);
        FontMetrics metrics = g2.getFontMetrics(MinecraftUiFont.font(fontSize));
        int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
        MinecraftUiFont.draw(
                g2,
                MinecraftUiFont.toSmallCaps(getText()),
                boxX + box + MinecraftTheme.scale(8),
                baseline,
                fontSize,
                textColor,
                false
        );
        g2.dispose();
    }
}
