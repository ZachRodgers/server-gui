package com.zach.minecraft.servergui.ui;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JTextField;
import javax.swing.Timer;

final class MinecraftTextField extends JTextField {
    private final String placeholder;
    private final int fontScale;

    MinecraftTextField(String placeholder, int columns, int fontScale) {
        super(columns);
        this.placeholder = placeholder;
        this.fontScale = fontScale;
        setOpaque(false);
        setBorder(null);
        setCaretColor(new java.awt.Color(0, 0, 0, 0));
        setForeground(new java.awt.Color(0, 0, 0, 0));
        setSelectedTextColor(new java.awt.Color(0, 0, 0, 0));
        setSelectionColor(new java.awt.Color(0, 0, 0, 0));
        Timer timer = new Timer(500, e -> repaint());
        timer.start();
    }

    @Override
    public Dimension getPreferredSize() {
        int unit = Math.max(1, MinecraftTheme.scale(fontScale));
        int width = MinecraftTheme.scale(super.getPreferredSize().width);
        width += (unit - (width % unit)) % unit;
        return new Dimension(width, MinecraftTheme.scale(20 * fontScale));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        MinecraftTheme.drawTextFieldSprite(
                g2,
                isFocusOwner() ? MinecraftTheme.TEXT_FIELD_FOCUS : MinecraftTheme.TEXT_FIELD,
                0,
                0,
                getWidth(),
                getHeight()
        );

        String text = getText();
        float fontSize = MinecraftUiFont.scaledSize(fontScale);
        FontMetrics metrics = g2.getFontMetrics(MinecraftUiFont.font(fontSize));
        int textX = MinecraftTheme.scale(4 * fontScale);
        int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
        if (text == null || text.isEmpty()) {
            MinecraftUiFont.draw(g2, placeholder, textX, baseline, fontSize, MinecraftTheme.TEXT_MUTED, false);
        } else {
            MinecraftUiFont.draw(g2, text, textX, baseline, fontSize, MinecraftTheme.PANEL_TEXT, false);
        }

        if (isFocusOwner() && ((System.currentTimeMillis() / 500) % 2 == 0)) {
            int caret = Math.min(getCaretPosition(), text == null ? 0 : text.length());
            int caretX = textX + MinecraftUiFont.textWidth(text == null ? "" : text.substring(0, caret), fontSize);
            g2.setColor(MinecraftTheme.PANEL_TEXT);
            g2.fillRect(caretX, baseline - metrics.getAscent(), Math.max(1, MinecraftTheme.scale(1)), metrics.getHeight());
        }
        g2.dispose();
    }
}
