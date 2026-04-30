package com.servergui.ui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

final class PlayerHeadCache {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final BufferedImage PLACEHOLDER = AppIconFactory.createImage(16);

    private PlayerHeadCache() {}

    static BufferedImage get(String playerName, Runnable repaint) {
        if (playerName == null || playerName.isBlank()) return PLACEHOLDER;
        BufferedImage cached = CACHE.get(playerName);
        if (cached != null) return cached;

        if (IN_FLIGHT.putIfAbsent(playerName, Boolean.TRUE) == null) {
            EXECUTOR.submit(() -> {
                try {
                    BufferedImage image = ImageIO.read(new URL("https://mineskin.eu/helm/" + playerName));
                    if (image != null) CACHE.put(playerName, image);
                } catch (IOException ignored) {
                    CACHE.put(playerName, PLACEHOLDER);
                } finally {
                    IN_FLIGHT.remove(playerName);
                    SwingUtilities.invokeLater(repaint);
                }
            });
        }
        return PLACEHOLDER;
    }
}
