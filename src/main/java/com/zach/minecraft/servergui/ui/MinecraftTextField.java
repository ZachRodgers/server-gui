package com.zach.minecraft.servergui.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JTextField;
import javax.swing.Timer;

final class MinecraftTextField extends JTextField {
    private final String placeholder;
    private final int scale;

    MinecraftTextField(String placeholder, int columns, int scale) {
        super(columns);
        this.placeholder = placeholder;
        this.scale = scale;
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
        return new Dimension(super.getPreferredSize().width, 20 * scale);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        MinecraftTheme.drawHorizontalSlice(
                g2,
                isFocusOwner() ? MinecraftTheme.TEXT_FIELD_FOCUS : MinecraftTheme.TEXT_FIELD,
                0,
                0,
                getWidth(),
                getHeight(),
                2
        );

        String text = getText();
        int textX = 4 * scale;
        int textY = (getHeight() - MinecraftFont.lineHeight(scale)) / 2;
        if (text == null || text.isEmpty()) {
            MinecraftFont.drawString(g2, placeholder.toUpperCase(), textX, textY, scale, MinecraftTheme.TEXT_MUTED, false);
        } else {
            MinecraftFont.drawString(g2, text, textX, textY, scale, MinecraftTheme.PANEL_TEXT, false);
        }

        if (isFocusOwner() && ((System.currentTimeMillis() / 500) % 2 == 0)) {
            int caret = Math.min(getCaretPosition(), text == null ? 0 : text.length());
            int caretX = textX + MinecraftFont.textWidth(text == null ? "" : text.substring(0, caret), scale);
            g2.setColor(MinecraftTheme.PANEL_TEXT);
            g2.fillRect(caretX, textY, scale, MinecraftFont.lineHeight(scale));
        }
        g2.dispose();
    }
}
