package com.zach.minecraft.servergui.ui;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Cursor;
import javax.swing.JButton;

final class MinecraftLinkButton extends JButton {
    MinecraftLinkButton(String text) {
        super(text);
        setOpaque(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override
    public Dimension getPreferredSize() {
        float fontSize = MinecraftUiFont.scaledSize(2);
        int width = MinecraftUiFont.textWidth(MinecraftUiFont.toSmallCaps(getText()), fontSize);
        return new Dimension(width, MinecraftUiFont.lineHeight(fontSize) + MinecraftTheme.scale(4));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        float fontSize = MinecraftUiFont.scaledSize(2);
        String text = MinecraftUiFont.toSmallCaps(getText());
        FontMetrics metrics = g2.getFontMetrics(MinecraftUiFont.font(fontSize));
        int baseline = metrics.getAscent();
        java.awt.Color color = getModel().isRollover() ? MinecraftTheme.PANEL_TEXT : MinecraftTheme.TEXT_MUTED;
        MinecraftUiFont.draw(g2, text, 0, baseline, fontSize, color, false);
        int underlineY = baseline + MinecraftTheme.scale(2);
        int underlineHeight = Math.max(1, MinecraftTheme.scale(1));
        int underlineWidth = MinecraftUiFont.textWidth(text, fontSize);
        g2.setColor(color);
        g2.fillRect(0, underlineY, underlineWidth, underlineHeight);
        g2.dispose();
    }
}
