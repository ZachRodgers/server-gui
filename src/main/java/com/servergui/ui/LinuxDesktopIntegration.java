package com.servergui.ui;

import com.servergui.config.AppConfig;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.ImageIO;

public final class LinuxDesktopIntegration {
    public static final String WM_CLASS = "com-servergui-Main";
    private static final String DESKTOP_FILE_NAME = "server-gui.desktop";
    private static final String ICON_FILE_NAME = "server-gui.png";

    private LinuxDesktopIntegration() {}

    public static void install(AppConfig config) {
        if (!isLinux()) return;

        try {
            Path jarPath = resolveJarPath();
            if (jarPath == null) return;

            Path applicationsDir = resolveApplicationsDir();
            Path iconsDir = resolveIconsDir();
            Files.createDirectories(applicationsDir);
            Files.createDirectories(iconsDir);

            Path iconPath = iconsDir.resolve(ICON_FILE_NAME);
            writeIcon(iconPath);

            String exec = "java -Dsun.awt.X11.XWMClass=" + WM_CLASS + " -jar \"" + jarPath.toAbsolutePath() + "\"";
            String desktopEntry = """
                    [Desktop Entry]
                    Version=1.0
                    Type=Application
                    Name=%s
                    Comment=Minecraft server console wrapper
                    Exec=%s
                    Path=%s
                    Icon=%s
                    Terminal=false
                    Categories=Game;Utility;
                    StartupNotify=true
                    StartupWMClass=%s
                    """.formatted(
                    escapeDesktopValue(config.appTitle()),
                    exec,
                    escapeDesktopValue(config.workingDirectory().toAbsolutePath().toString()),
                    escapeDesktopValue(iconPath.toAbsolutePath().toString()),
                    WM_CLASS
            );

            Files.writeString(applicationsDir.resolve(DESKTOP_FILE_NAME), desktopEntry, StandardCharsets.UTF_8);
            refreshDesktopDatabase(applicationsDir);
        } catch (IOException | URISyntaxException ignored) {}
    }

    private static void refreshDesktopDatabase(Path applicationsDir) {
        try {
            new ProcessBuilder("update-desktop-database", applicationsDir.toAbsolutePath().toString()).start();
        } catch (IOException ignored) {
        }
    }

    private static void writeIcon(Path iconPath) throws IOException {
        BufferedImage image = AppIconFactory.createImage(256);
        ImageIO.write(image, "png", iconPath.toFile());
    }

    private static Path resolveJarPath() throws URISyntaxException {
        Path path = Path.of(LinuxDesktopIntegration.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return Files.isRegularFile(path) ? path : null;
    }

    private static Path resolveApplicationsDir() {
        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        if (xdgDataHome != null && !xdgDataHome.isBlank()) {
            return Path.of(xdgDataHome).resolve("applications");
        }
        return Path.of(System.getProperty("user.home"), ".local", "share", "applications");
    }

    private static Path resolveIconsDir() {
        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        if (xdgDataHome != null && !xdgDataHome.isBlank()) {
            return Path.of(xdgDataHome).resolve("icons").resolve("hicolor").resolve("256x256").resolve("apps");
        }
        return Path.of(System.getProperty("user.home"), ".local", "share", "icons", "hicolor", "256x256", "apps");
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }

    private static String escapeDesktopValue(String value) {
        return value.replace("\\", "\\\\").replace("\n", " ");
    }
}
