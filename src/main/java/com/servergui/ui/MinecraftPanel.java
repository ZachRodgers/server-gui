package com.servergui.ui;

import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JPanel;

final class MinecraftPanel extends JPanel {
    private final boolean listTexture;
    private final float alpha;

    MinecraftPanel(boolean listTexture, float alpha) {
        super(new BorderLayout());
        this.listTexture = listTexture;
        this.alpha = alpha;
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics.create();
        MinecraftTheme.paintTiledBackground(
                g2,
                listTexture ? MinecraftTheme.LIST_BG : MinecraftTheme.WINDOW_BG,
                0,
                0,
                getWidth(),
                getHeight(),
                alpha
        );
        int stroke = Math.max(2, MinecraftTheme.scale(2));
        g2.setColor(MinecraftTheme.BORDER_LIGHT);
        g2.fillRect(0, 0, getWidth(), stroke);
        g2.fillRect(0, getHeight() - stroke, getWidth(), stroke);
        g2.fillRect(0, 0, stroke, getHeight());
        g2.fillRect(getWidth() - stroke, 0, stroke, getHeight());
        g2.dispose();
    }
}
