package com.servergui.ui;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Cursor;
import javax.swing.JButton;

final class MinecraftButton extends JButton {
    private final int fontScale;
    private boolean smallCaps = true;

    MinecraftButton(String text, int fontScale) {
        super(text);
        this.fontScale = fontScale;
        setOpaque(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setMargin(new Insets(0, 0, 0, 0));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override
    public Dimension getPreferredSize() {
        float fontSize = MinecraftUiFont.scaledSize(fontScale);
        int textWidth = MinecraftUiFont.textWidth(displayText(), fontSize);
        int unit = Math.max(1, MinecraftTheme.scale(fontScale));
        int width = Math.max(MinecraftTheme.scale(72), textWidth + MinecraftTheme.scale(12 * fontScale));
        width += (unit - (width % unit)) % unit;
        int height = MinecraftTheme.scale(20 * fontScale);
        return new Dimension(width, height);
    }

    void setSmallCaps(boolean smallCaps) {
        this.smallCaps = smallCaps;
        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        MinecraftTheme.drawWidgetSprite(
                g2,
                !isEnabled() ? MinecraftTheme.BUTTON_DISABLED : getModel().isRollover() || getModel().isPressed()
                        ? MinecraftTheme.BUTTON_HOVER
                        : MinecraftTheme.BUTTON,
                0,
                0,
                getWidth(),
                getHeight()
        );
        float fontSize = MinecraftUiFont.scaledSize(fontScale);
        String text = displayText();
        FontMetrics metrics = g2.getFontMetrics(MinecraftUiFont.font(fontSize));
        int textWidth = MinecraftUiFont.textWidth(text, fontSize);
        int textX = (getWidth() - textWidth) / 2;
        int pressOffset = getModel().isPressed() ? Math.max(1, MinecraftTheme.scale(fontScale) / 2) : 0;
        int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent() + pressOffset;
        MinecraftUiFont.draw(g2, text, textX, baseline, fontSize, isEnabled() ? MinecraftTheme.PANEL_TEXT : MinecraftTheme.TEXT_MUTED, true);
        g2.dispose();
    }

    private String displayText() {
        String value = getText() == null ? "" : getText();
        return smallCaps ? MinecraftUiFont.toSmallCaps(value) : value;
    }
}
