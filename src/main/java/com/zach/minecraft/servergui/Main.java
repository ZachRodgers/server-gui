package com.zach.minecraft.servergui;

import com.formdev.flatlaf.FlatLightLaf;
import com.zach.minecraft.servergui.config.AppConfig;
import com.zach.minecraft.servergui.ui.MainFrame;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        AppConfig config = AppConfig.load(Path.of(".").toAbsolutePath().normalize());
        installLookAndFeel();
        SwingUtilities.invokeLater(() -> new MainFrame(config).setVisible(true));
    }

    private static void installLookAndFeel() {
        try {
            FlatLightLaf.setup();
            UIManager.put("Component.arc", 14);
            UIManager.put("TextComponent.arc", 12);
            UIManager.put("Button.arc", 16);
        } catch (Exception ignored) {
            UIManager.getLookAndFeelDefaults();
        }
    }
}
