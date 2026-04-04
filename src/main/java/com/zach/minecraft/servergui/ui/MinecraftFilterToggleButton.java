package com.zach.minecraft.servergui.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Cursor;
import java.awt.image.BufferedImage;
import javax.swing.JButton;

final class MinecraftFilterToggleButton extends JButton {
    MinecraftFilterToggleButton() {
        setOpaque(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setToolTipText("Search and filter");
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override
    public Dimension getPreferredSize() {
        int size = MinecraftTheme.scale(44);
        return new Dimension(size, size);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        MinecraftTheme.drawWidgetSprite(
                g2,
                getModel().isRollover() || getModel().isPressed() ? MinecraftTheme.BUTTON_HOVER : MinecraftTheme.BUTTON,
                0,
                0,
                getWidth(),
                getHeight()
        );
        BufferedImage icon = MinecraftTheme.FILTER_ICON;
        int iconSize = Math.max(MinecraftTheme.scale(18), MinecraftUiFont.lineHeight(MinecraftUiFont.scaledSize(2)));
        int x = (getWidth() - iconSize) / 2;
        int y = (getHeight() - iconSize) / 2;
        MinecraftTheme.applyPixelRendering(g2);
        g2.drawImage(icon, x, y, x + iconSize, y + iconSize, 0, 0, icon.getWidth(), icon.getHeight(), null);
        g2.dispose();
    }
}
