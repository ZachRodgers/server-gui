package com.zach.minecraft.servergui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.zach.minecraft.servergui.config.AppConfig;
import com.zach.minecraft.servergui.ui.MainFrame;
import com.zach.minecraft.servergui.ui.LinuxDesktopIntegration;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        AppConfig config = AppConfig.load(Path.of(".").toAbsolutePath().normalize());
        LinuxDesktopIntegration.install(config);
        installLookAndFeel();
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(config);
            frame.setVisible(true);
            frame.autoStart();
        });
    }

    private static void installLookAndFeel() {
        try {
            FlatDarkLaf.setup();
            UIManager.put("Component.arc", 8);
            UIManager.put("TextComponent.arc", 8);
            UIManager.put("Button.arc", 8);
        } catch (Exception ignored) {
            UIManager.getLookAndFeelDefaults();
        }
    }
}
