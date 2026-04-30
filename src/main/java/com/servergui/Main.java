package com.servergui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.servergui.config.AppConfig;
import com.servergui.ui.MainFrame;
import com.servergui.ui.LinuxDesktopIntegration;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        if (relaunchWithWindowClassIfNeeded(args)) return;
        System.setProperty("sun.awt.X11.XWMClass", LinuxDesktopIntegration.WM_CLASS);
        AppConfig config = AppConfig.load(Path.of(".").toAbsolutePath().normalize());
        LinuxDesktopIntegration.install(config);
        installLookAndFeel();
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(config);
            frame.setVisible(true);
            frame.autoStart();
        });
    }

    private static boolean relaunchWithWindowClassIfNeeded(String[] args) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) return false;
        if (LinuxDesktopIntegration.WM_CLASS.equals(System.getProperty("sun.awt.X11.XWMClass"))) return false;

        try {
            Path jarPath = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!Files.isRegularFile(jarPath)) return false;

            List<String> command = new ArrayList<>();
            command.add(resolveJavaCommand());
            command.add("-Dsun.awt.X11.XWMClass=" + LinuxDesktopIntegration.WM_CLASS);
            command.add("-jar");
            command.add(jarPath.toAbsolutePath().toString());
            command.addAll(List.of(args));

            new ProcessBuilder(command)
                    .directory(Path.of(System.getProperty("user.dir")).toFile())
                    .start();
            return true;
        } catch (IOException | URISyntaxException ignored) {
            return false;
        }
    }

    private static String resolveJavaCommand() {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        if (Files.isExecutable(java)) return java.toString();
        return "java";
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
