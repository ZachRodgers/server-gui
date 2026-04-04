package com.zach.minecraft.servergui.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.TexturePaint;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

final class MinecraftTheme {
    static final Color BG = new Color(0x1B1B1B);
    static final Color PANEL_TEXT = new Color(0xFFFFFF);
    static final Color TEXT_MUTED = new Color(0xA0A0A0);
    static final Color TEXT_DARK = new Color(0x3F3F3F);
    static final Color INFO = new Color(0xFFFFFF);
    static final Color WARN = new Color(0xFFAA00);
    static final Color ERROR = new Color(0xFF5555);
    static final Color SUCCESS = new Color(0x55FF55);
    static final Color SELECTION = new Color(0x6C6C6C);
    static final Color CHAT = new Color(0x55FFFF);
    static final Color COMMAND = new Color(0xFF55FF);

    static final BufferedImage BUTTON = load("assets/minecraft/textures/gui/sprites/widget/button.png");
    static final BufferedImage BUTTON_HOVER = load("assets/minecraft/textures/gui/sprites/widget/button_highlighted.png");
    static final BufferedImage BUTTON_DISABLED = load("assets/minecraft/textures/gui/sprites/widget/button_disabled.png");
    static final BufferedImage TEXT_FIELD = load("assets/minecraft/textures/gui/sprites/widget/text_field.png");
    static final BufferedImage TEXT_FIELD_FOCUS = load("assets/minecraft/textures/gui/sprites/widget/text_field_highlighted.png");
    static final BufferedImage SCROLLER = load("assets/minecraft/textures/gui/sprites/widget/scroller.png");
    static final BufferedImage SCROLLER_BG = load("assets/minecraft/textures/gui/sprites/widget/scroller_background.png");
    static final BufferedImage SLOT = load("assets/minecraft/textures/gui/sprites/widget/slot_frame.png");
    static final BufferedImage WINDOW_BG = load("assets/minecraft/textures/gui/inworld_menu_background.png");
    static final BufferedImage LIST_BG = load("assets/minecraft/textures/gui/menu_list_background.png");
    static final BufferedImage ICON = load("icons/server-gui.png");

    private MinecraftTheme() {}

    static BufferedImage load(String resourcePath) {
        try (InputStream input = MinecraftTheme.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) throw new IllegalStateException("Missing resource: " + resourcePath);
            BufferedImage image = ImageIO.read(input);
            if (image == null) throw new IllegalStateException("Unreadable resource: " + resourcePath);
            return image;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load resource: " + resourcePath, exception);
        }
    }

    static void applyPixelRendering(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
    }

    static void paintTiledBackground(Graphics2D g2, BufferedImage tile, int x, int y, int width, int height, float alpha) {
        Graphics2D copy = (Graphics2D) g2.create();
        applyPixelRendering(copy);
        copy.setComposite(AlphaComposite.SrcOver.derive(alpha));
        copy.setPaint(new TexturePaint(tile, new java.awt.Rectangle(x, y, tile.getWidth(), tile.getHeight())));
        copy.fillRect(x, y, width, height);
        copy.dispose();
    }

    static void drawStretch(Graphics2D g2, BufferedImage image, int x, int y, int width, int height) {
        applyPixelRendering(g2);
        g2.drawImage(image.getScaledInstance(width, height, Image.SCALE_FAST), x, y, null);
    }

    static void drawWidgetSprite(Graphics2D g2, BufferedImage image, int x, int y, int width, int height) {
        applyPixelRendering(g2);
        int srcW = image.getWidth();
        int srcH = image.getHeight();
        int scale = Math.max(1, height / srcH);
        int logicalWidth = Math.max(2, Math.round((float) width / scale));
        int leftLogical = logicalWidth / 2;
        int rightLogical = logicalWidth - leftLogical;
        int leftSrc = Math.min(leftLogical, srcW);
        int rightSrc = Math.min(rightLogical, srcW - leftSrc);
        int leftDest = leftLogical * scale;
        int rightDest = width - leftDest;

        if (rightSrc <= 0) {
            g2.drawImage(image, x, y, x + width, y + height, 0, 0, Math.min(logicalWidth, srcW), srcH, null);
            return;
        }

        g2.drawImage(image, x, y, x + leftDest, y + height, 0, 0, leftSrc, srcH, null);
        g2.drawImage(image, x + leftDest, y, x + width, y + height, srcW - rightSrc, 0, srcW, srcH, null);
    }

    static void drawTextFieldSprite(Graphics2D g2, BufferedImage image, int x, int y, int width, int height) {
        applyPixelRendering(g2);
        int srcW = image.getWidth();
        int srcH = image.getHeight();
        int scale = Math.max(1, height / srcH);
        int cap = Math.max(2, 4 * scale);
        int srcCap = Math.max(2, Math.min(4, srcW / 4));
        int middleStart = srcCap;
        int middleEnd = srcW - srcCap;

        g2.drawImage(image, x, y, x + cap, y + height, 0, 0, srcCap, srcH, null);
        g2.drawImage(image, x + width - cap, y, x + width, y + height, srcW - srcCap, 0, srcW, srcH, null);

        int midWidth = Math.max(0, width - (cap * 2));
        if (midWidth <= 0) return;

        int destX = x + cap;
        int srcTileWidth = Math.max(1, middleEnd - middleStart);
        while (midWidth > 0) {
            int drawWidth = Math.min(scale, midWidth);
            g2.drawImage(image, destX, y, destX + drawWidth, y + height, middleStart, 0, middleStart + 1, srcH, null);
            destX += drawWidth;
            midWidth -= drawWidth;
            if (srcTileWidth <= 1) break;
        }
    }
}
