package com.zach.minecraft.servergui.ui;

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
        g2.setColor(MinecraftTheme.TEXT_DARK);
        g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
        g2.dispose();
    }
}
