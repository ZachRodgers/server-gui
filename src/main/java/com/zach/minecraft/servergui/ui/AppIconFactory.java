package com.zach.minecraft.servergui.ui;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.imageio.ImageIO;

final class AppIconFactory {
    private static final String ICON_RESOURCE = "/icons/server-gui.png";

    private AppIconFactory() {}

    static List<Image> createIcons() {
        BufferedImage source = loadSourceImage();
        return List.of(
                source.getScaledInstance(16, 16, Image.SCALE_SMOOTH),
                source.getScaledInstance(24, 24, Image.SCALE_SMOOTH),
                source.getScaledInstance(32, 32, Image.SCALE_SMOOTH),
                source.getScaledInstance(48, 48, Image.SCALE_SMOOTH),
                source.getScaledInstance(64, 64, Image.SCALE_SMOOTH),
                source.getScaledInstance(128, 128, Image.SCALE_SMOOTH),
                source
        );
    }

    static BufferedImage createImage(int size) {
        BufferedImage source = loadSourceImage();
        if (source.getWidth() == size && source.getHeight() == size) {
            return source;
        }

        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        scaled.getGraphics().drawImage(source.getScaledInstance(size, size, Image.SCALE_SMOOTH), 0, 0, null);
        return scaled;
    }

    private static BufferedImage loadSourceImage() {
        try (InputStream input = AppIconFactory.class.getResourceAsStream(ICON_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing icon resource: " + ICON_RESOURCE);
            }
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IllegalStateException("Unreadable icon resource: " + ICON_RESOURCE);
            }
            return image;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load icon resource: " + ICON_RESOURCE, e);
        }
    }
}
