package com.zach.minecraft.servergui.ui;

import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

final class MinecraftSplitPaneUI extends BasicSplitPaneUI {
    @Override
    public BasicSplitPaneDivider createDefaultDivider() {
        BasicSplitPaneDivider divider = new BasicSplitPaneDivider(this) {
            @Override
            public void setBorder(javax.swing.border.Border border) {
            }

            @Override
            public void paint(Graphics graphics) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setColor(MinecraftTheme.TEXT_DARK);

                int thickness = Math.max(2, MinecraftTheme.scale(2));
                int length = MinecraftTheme.scale(12);

                if (orientation == javax.swing.JSplitPane.HORIZONTAL_SPLIT) {
                    int x = (getWidth() - thickness) / 2;
                    int y = (getHeight() - length) / 2;
                    g2.fillRect(x, y, thickness, length);
                } else {
                    int x = (getWidth() - length) / 2;
                    int y = (getHeight() - thickness) / 2;
                    g2.fillRect(x, y, length, thickness);
                }
                g2.dispose();
            }
        };
        divider.setCursor(splitPane != null && splitPane.getOrientation() == javax.swing.JSplitPane.HORIZONTAL_SPLIT
                ? Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
                : Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR));
        return divider;
    }

    @Override
    protected JButton createDefaultNonContinuousLayoutDivider() {
        JButton button = new JButton();
        button.setBorder(null);
        button.setOpaque(false);
        button.setFocusable(false);
        return button;
    }

    @Override
    public void installUI(JComponent component) {
        super.installUI(component);
        if (splitPane != null) {
            splitPane.setCursor(Cursor.getDefaultCursor());
        }
    }
}
