package com.zach.minecraft.servergui.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import javax.swing.JButton;

final class MinecraftButton extends JButton {
    private final int scale;

    MinecraftButton(String text, int scale) {
        super(text);
        this.scale = scale;
        setOpaque(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setMargin(new Insets(0, 0, 0, 0));
    }

    @Override
    public Dimension getPreferredSize() {
        int width = Math.max(120, MinecraftFont.textWidth(getText(), scale) + (18 * scale));
        int height = 20 * scale;
        return new Dimension(width, height);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        MinecraftTheme.drawHorizontalSlice(
                g2,
                !isEnabled() ? MinecraftTheme.BUTTON_DISABLED : getModel().isRollover() || getModel().isPressed()
                        ? MinecraftTheme.BUTTON_HOVER
                        : MinecraftTheme.BUTTON,
                0,
                0,
                getWidth(),
                getHeight(),
                2
        );
        String text = getText() == null ? "" : getText().toUpperCase();
        int textWidth = MinecraftFont.textWidth(text, scale);
        int textX = (getWidth() - textWidth) / 2;
        int textY = (getHeight() - MinecraftFont.lineHeight(scale)) / 2 + (getModel().isPressed() ? scale : 0);
        MinecraftFont.drawString(
                g2,
                text,
                textX,
                textY,
                scale,
                isEnabled() ? MinecraftTheme.PANEL_TEXT : MinecraftTheme.TEXT_MUTED,
                true
        );
        g2.dispose();
    }
}
