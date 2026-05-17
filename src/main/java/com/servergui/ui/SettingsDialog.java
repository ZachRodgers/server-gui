package com.servergui.ui;

import com.servergui.config.AppConfig;
import com.servergui.config.ServerProperties;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

final class SettingsDialog extends JDialog {
    private static final int CONTROL_FONT_SCALE = 2;

    private final AppConfig initialConfig;
    private final BiConsumer<AppConfig, Boolean> onApply;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel content = new JPanel(cardLayout);
    private final List<Tab> tabs = new ArrayList<>();
    private final List<MinecraftButton> tabButtons = new ArrayList<>();
    private Tab activeTab;

    private final MinecraftButton applyButton = new MinecraftButton("Apply", CONTROL_FONT_SCALE);
    private final MinecraftButton applyRestartButton = new MinecraftButton("Apply and Restart", CONTROL_FONT_SCALE);
    private final MinecraftButton closeButton = new MinecraftButton("Close", CONTROL_FONT_SCALE);

    SettingsDialog(Frame owner, AppConfig config, BiConsumer<AppConfig, Boolean> onApply) {
        super(owner, "Settings", true);
        this.initialConfig = config;
        this.onApply = onApply;

        ConsoleTab consoleTab = new ConsoleTab(config);
        ServerPropertiesTab propsTab = new ServerPropertiesTab(config);
        PluginsTab pluginsTab = new PluginsTab(config);
        GitSyncTab gitSyncTab = new GitSyncTab(config);
        tabs.add(consoleTab);
        tabs.add(propsTab);
        tabs.add(pluginsTab);
        tabs.add(gitSyncTab);

        for (Tab tab : tabs) {
            content.add(tab.component(), tab.id());
        }
        content.setOpaque(false);

        setContentPane(buildRoot());
        selectTab(consoleTab);
        setMinimumSize(new Dimension(MinecraftTheme.scale(900), MinecraftTheme.scale(640)));
        pack();
        setSize(new Dimension(MinecraftTheme.scale(960), MinecraftTheme.scale(680)));
        setLocationRelativeTo(owner);
    }

    private JComponent buildRoot() {
        JPanel root = new MinecraftPanel(true, 0.97f);
        root.setBorder(new EmptyBorder(MinecraftTheme.scale(12), MinecraftTheme.scale(12), MinecraftTheme.scale(12), MinecraftTheme.scale(12)));

        root.add(buildSidebar(), BorderLayout.WEST);
        root.add(content, BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        return root;
    }

    private JComponent buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setOpaque(false);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(new EmptyBorder(0, 0, 0, MinecraftTheme.scale(14)));

        for (Tab tab : tabs) {
            MinecraftButton button = new MinecraftButton(tab.title(), CONTROL_FONT_SCALE);
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.setMaximumSize(new Dimension(MinecraftTheme.scale(220), MinecraftTheme.scale(44)));
            button.setPreferredSize(new Dimension(MinecraftTheme.scale(200), MinecraftTheme.scale(40)));
            button.addActionListener(e -> selectTab(tab));
            tabButtons.add(button);
            sidebar.add(button);
            sidebar.add(javax.swing.Box.createVerticalStrut(MinecraftTheme.scale(8)));
        }
        sidebar.add(javax.swing.Box.createVerticalGlue());
        return sidebar;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, MinecraftTheme.scale(8), 0));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(MinecraftTheme.scale(10), 0, 0, 0));
        applyButton.addActionListener(e -> apply(false));
        applyRestartButton.addActionListener(e -> apply(true));
        closeButton.addActionListener(e -> dispose());
        footer.add(closeButton);
        footer.add(applyButton);
        footer.add(applyRestartButton);
        return footer;
    }

    private void selectTab(Tab tab) {
        this.activeTab = tab;
        cardLayout.show(content, tab.id());
        for (int i = 0; i < tabs.size(); i++) {
            tabButtons.get(i).setEnabled(tabs.get(i) != tab);
        }
        applyButton.setVisible(tab.supportsApply());
        applyRestartButton.setVisible(tab.supportsApply());
    }

    private void apply(boolean restartServer) {
        if (activeTab == null) return;
        try {
            AppConfig updated = activeTab.apply(initialConfig);
            onApply.accept(updated, restartServer);
            dispose();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid settings", JOptionPane.ERROR_MESSAGE);
        } catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Tab interface ──────────────────────────────────────────────────────
    private interface Tab {
        String id();
        String title();
        JComponent component();
        AppConfig apply(AppConfig current);
        default boolean supportsApply() { return true; }
    }

    // ── Console tab ────────────────────────────────────────────────────────
    private static final class ConsoleTab implements Tab {
        private final JPanel panel = new JPanel(new BorderLayout());
        private final MinecraftTextField titleField  = new MinecraftTextField("Window name", 24, CONTROL_FONT_SCALE);
        private final MinecraftTextField playersPoll = new MinecraftTextField("seconds", 8, CONTROL_FONT_SCALE);
        private final MinecraftTextField tpsPoll     = new MinecraftTextField("seconds", 8, CONTROL_FONT_SCALE);
        private final MinecraftTextField memoryPoll  = new MinecraftTextField("seconds", 8, CONTROL_FONT_SCALE);
        private final MinecraftTextField minHeap     = new MinecraftTextField("3G", 8, CONTROL_FONT_SCALE);
        private final MinecraftTextField maxHeap     = new MinecraftTextField("6G", 8, CONTROL_FONT_SCALE);

        ConsoleTab(AppConfig config) {
            panel.setOpaque(false);
            titleField.setText(config.appTitle());
            playersPoll.setText(String.valueOf(config.playerPollSeconds()));
            tpsPoll.setText(String.valueOf(config.tpsPollSeconds()));
            memoryPoll.setText(String.valueOf(config.heapPollSeconds()));
            minHeap.setText(config.minHeap());
            maxHeap.setText(config.maxHeap());

            JPanel form = new JPanel(new GridBagLayout());
            form.setOpaque(false);
            GridBagConstraints g = new GridBagConstraints();
            g.gridx = 0; g.gridy = 0;
            g.anchor = GridBagConstraints.WEST;
            g.fill = GridBagConstraints.HORIZONTAL;
            g.insets = new Insets(0, 0, MinecraftTheme.scale(8), MinecraftTheme.scale(10));
            addRow(form, g, "Window name", titleField, null);
            addRow(form, g, "Poll players", playersPoll, "seconds");
            addRow(form, g, "Poll TPS", tpsPoll, "seconds");
            addRow(form, g, "Poll memory", memoryPoll, "seconds");
            addRow(form, g, "Min heap", minHeap, null);
            addRow(form, g, "Max heap", maxHeap, null);

            MinecraftLabel header = new MinecraftLabel("Console", 4);
            header.setSmallCaps(true);
            MinecraftLabel note = new MinecraftLabel("polls apply now, heap changes need restart", 1);
            note.setPixelColor(MinecraftTheme.TEXT_MUTED);
            note.setShadow(false);
            JPanel head = new JPanel();
            head.setOpaque(false);
            head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
            head.add(header);
            head.add(javax.swing.Box.createVerticalStrut(MinecraftTheme.scale(8)));
            head.add(note);
            head.setBorder(new EmptyBorder(0, 0, MinecraftTheme.scale(16), 0));

            panel.add(head, BorderLayout.NORTH);
            panel.add(form, BorderLayout.CENTER);
        }

        private static void addRow(JPanel form, GridBagConstraints g, String labelText, JComponent field, String suffix) {
            MinecraftLabel label = new MinecraftLabel(labelText, 2);
            label.setSmallCaps(true);
            g.gridx = 0; g.weightx = 0;
            form.add(label, g);
            g.gridx = 1; g.weightx = 1;
            form.add(field, g);
            if (suffix != null) {
                MinecraftLabel s = new MinecraftLabel(suffix, 2);
                s.setPixelColor(MinecraftTheme.TEXT_MUTED);
                s.setShadow(false);
                g.gridx = 2; g.weightx = 0;
                form.add(s, g);
            }
            g.gridy++;
        }

        @Override public String id() { return "console"; }
        @Override public String title() { return "Console"; }
        @Override public JComponent component() { return panel; }

        @Override
        public AppConfig apply(AppConfig current) {
            String title = require(titleField.getText(), "Window name");
            int players = nonNeg(playersPoll.getText(), "Poll players");
            int tps = nonNeg(tpsPoll.getText(), "Poll TPS");
            int memory = nonNeg(memoryPoll.getText(), "Poll memory");
            String min = require(minHeap.getText(), "Min heap");
            String max = require(maxHeap.getText(), "Max heap");
            validateHeap(min, max);
            return current.save(title, players, tps, memory, min, max);
        }

        private static String require(String text, String name) {
            String t = text == null ? "" : text.trim();
            if (t.isBlank()) throw new IllegalArgumentException(name + " cannot be blank.");
            return t;
        }
        private static int nonNeg(String text, String name) {
            try {
                int v = Integer.parseInt(require(text, name));
                if (v < 0) throw new IllegalArgumentException(name + " must be 0 or greater.");
                return v;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(name + " must be a whole number.");
            }
        }
        private static void validateHeap(String min, String max) {
            if (heapMb(min, "Min heap") > heapMb(max, "Max heap")) {
                throw new IllegalArgumentException("Min heap cannot be larger than max heap.");
            }
        }
        private static long heapMb(String value, String name) {
            String t = value.trim().toUpperCase(Locale.ROOT);
            if (!t.matches("\\d+[KMG]")) throw new IllegalArgumentException(name + " must use a number followed by K, M, or G.");
            long amt = Long.parseLong(t.substring(0, t.length() - 1));
            return switch (t.charAt(t.length() - 1)) {
                case 'G' -> amt * 1024L;
                case 'M' -> amt;
                default  -> Math.max(1L, amt / 1024L);
            };
        }
    }

    // ── Server Properties tab ──────────────────────────────────────────────
    private static final class ServerPropertiesTab implements Tab {
        private static final List<String> COMMON = List.of(
                "motd", "max-players", "difficulty", "gamemode", "hardcore", "pvp", "online-mode",
                "white-list", "view-distance", "simulation-distance", "server-port", "level-name",
                "allow-flight", "spawn-protection"
        );
        private static final Set<String> COMMON_SET = new LinkedHashSet<>(COMMON);

        private final JPanel panel = new JPanel(new BorderLayout());
        private final ServerProperties properties;
        private final Map<String, JComponent> editors = new LinkedHashMap<>();
        private final JPanel advancedHolder = new JPanel(new BorderLayout());
        private boolean advancedOpen;
        private final MinecraftButton advancedToggle = new MinecraftButton("Show advanced", CONTROL_FONT_SCALE);
        private final boolean available;

        ServerPropertiesTab(AppConfig config) {
            panel.setOpaque(false);
            Path path = config.workingDirectory().resolve("server.properties");
            this.properties = ServerProperties.load(path);
            this.available = properties.exists();

            MinecraftLabel header = new MinecraftLabel("Server Properties", 4);
            header.setSmallCaps(true);
            JPanel head = new JPanel();
            head.setOpaque(false);
            head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
            head.add(header);
            head.add(javax.swing.Box.createVerticalStrut(MinecraftTheme.scale(6)));
            MinecraftLabel sub = new MinecraftLabel(available
                    ? "edits save to " + path.toString()
                    : "no server.properties found in working directory", 1);
            sub.setPixelColor(MinecraftTheme.TEXT_MUTED);
            sub.setShadow(false);
            head.add(sub);
            head.setBorder(new EmptyBorder(0, 0, MinecraftTheme.scale(14), 0));

            panel.add(head, BorderLayout.NORTH);

            if (!available) {
                return;
            }

            JPanel body = new JPanel();
            body.setOpaque(false);
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.add(buildSection("Common", COMMON.stream().filter(properties::contains).toList()));

            advancedToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
            advancedToggle.addActionListener(e -> toggleAdvanced());
            advancedHolder.setOpaque(false);
            advancedHolder.setVisible(false);
            advancedHolder.setAlignmentX(Component.LEFT_ALIGNMENT);

            body.add(javax.swing.Box.createVerticalStrut(MinecraftTheme.scale(12)));
            body.add(advancedToggle);
            body.add(javax.swing.Box.createVerticalStrut(MinecraftTheme.scale(8)));
            body.add(advancedHolder);

            JScrollPane scroll = new JScrollPane(body);
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
            scroll.setBorder(null);
            scroll.getVerticalScrollBar().setUI(new MinecraftScrollBarUI());
            scroll.getVerticalScrollBar().setUnitIncrement(MinecraftTheme.scale(16));
            panel.add(scroll, BorderLayout.CENTER);
        }

        private void toggleAdvanced() {
            advancedOpen = !advancedOpen;
            advancedToggle.setText(advancedOpen ? "Hide advanced" : "Show advanced");
            if (advancedOpen && advancedHolder.getComponentCount() == 0) {
                List<String> advanced = new ArrayList<>();
                for (String key : new TreeMap<>(properties.all()).keySet()) {
                    if (!COMMON_SET.contains(key)) advanced.add(key);
                }
                advancedHolder.add(buildSection("Advanced", advanced), BorderLayout.CENTER);
            }
            advancedHolder.setVisible(advancedOpen);
            advancedHolder.revalidate();
            advancedHolder.repaint();
        }

        private JComponent buildSection(String title, List<String> keys) {
            JPanel section = new JPanel();
            section.setOpaque(false);
            section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
            section.setAlignmentX(Component.LEFT_ALIGNMENT);

            MinecraftLabel label = new MinecraftLabel(title, 3);
            label.setSmallCaps(true);
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            section.add(label);
            section.add(javax.swing.Box.createVerticalStrut(MinecraftTheme.scale(8)));

            JPanel form = new JPanel(new GridBagLayout());
            form.setOpaque(false);
            form.setAlignmentX(Component.LEFT_ALIGNMENT);
            GridBagConstraints g = new GridBagConstraints();
            g.gridx = 0; g.gridy = 0;
            g.anchor = GridBagConstraints.WEST;
            g.fill = GridBagConstraints.HORIZONTAL;
            g.insets = new Insets(0, 0, MinecraftTheme.scale(6), MinecraftTheme.scale(10));

            for (String key : keys) {
                JComponent editor = editorFor(key, properties.get(key, ""));
                editors.put(key, editor);

                MinecraftLabel keyLabel = new MinecraftLabel(key, 2);
                keyLabel.setSmallCaps(false);
                g.gridx = 0; g.weightx = 0;
                form.add(keyLabel, g);
                g.gridx = 1; g.weightx = 1;
                form.add(editor, g);
                g.gridy++;
            }
            section.add(form);
            return section;
        }

        private static JComponent editorFor(String key, String value) {
            PropType type = PropType.forKey(key);
            return switch (type.kind) {
                case BOOL -> {
                    JCheckBox cb = new JCheckBox();
                    cb.setOpaque(false);
                    cb.setForeground(MinecraftTheme.PANEL_TEXT);
                    cb.setSelected(parseBool(value));
                    yield cb;
                }
                case INT -> {
                    int initial = parseInt(value, type.minInt);
                    JSpinner spinner = new JSpinner(new SpinnerNumberModel(
                            Math.max(type.minInt, Math.min(type.maxInt, initial)),
                            type.minInt, type.maxInt, 1));
                    spinner.setOpaque(false);
                    yield spinner;
                }
                case ENUM -> {
                    JComboBox<String> combo = new JComboBox<>(type.options);
                    combo.setSelectedItem(value);
                    yield combo;
                }
                case STRING -> {
                    JTextField text = new JTextField(value, 24);
                    text.setOpaque(true);
                    text.setBackground(new Color(0x1B1B1B));
                    text.setForeground(MinecraftTheme.PANEL_TEXT);
                    text.setCaretColor(MinecraftTheme.PANEL_TEXT);
                    text.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(1, 1, 1, 1, MinecraftTheme.BORDER_LIGHT),
                            new EmptyBorder(4, 6, 4, 6)));
                    yield text;
                }
            };
        }

        private static boolean parseBool(String v) {
            return "true".equalsIgnoreCase(v == null ? "" : v.trim());
        }
        private static int parseInt(String v, int fallback) {
            try { return Integer.parseInt(v == null ? "" : v.trim()); }
            catch (NumberFormatException ex) { return fallback; }
        }

        @Override public String id() { return "props"; }
        @Override public String title() { return "Server Properties"; }
        @Override public JComponent component() { return panel; }
        @Override public boolean supportsApply() { return available; }

        @Override
        public AppConfig apply(AppConfig current) {
            for (Map.Entry<String, JComponent> entry : editors.entrySet()) {
                String key = entry.getKey();
                JComponent c = entry.getValue();
                String value;
                if (c instanceof JCheckBox cb) value = String.valueOf(cb.isSelected());
                else if (c instanceof JSpinner s) value = String.valueOf(s.getValue());
                else if (c instanceof JComboBox<?> combo) value = String.valueOf(combo.getSelectedItem());
                else if (c instanceof JTextField t) value = t.getText();
                else continue;
                properties.set(key, value);
            }
            try {
                properties.save();
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to save server.properties: " + ex.getMessage(), ex);
            }
            return current;
        }
    }

    // ── Plugins tab ────────────────────────────────────────────────────────
    private static final class PluginsTab implements Tab {
        private final JPanel panel = new JPanel(new BorderLayout());
        private final Path pluginsDir;
        private final boolean compatible;

        PluginsTab(AppConfig config) {
            panel.setOpaque(false);
            this.pluginsDir = config.workingDirectory().resolve("plugins");
            this.compatible = isPluginServer(config) && Files.isDirectory(pluginsDir);

            MinecraftLabel header = new MinecraftLabel("Plugins", 4);
            header.setSmallCaps(true);
            JPanel head = new JPanel();
            head.setOpaque(false);
            head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
            head.add(header);
            head.setBorder(new EmptyBorder(0, 0, MinecraftTheme.scale(14), 0));
            panel.add(head, BorderLayout.NORTH);

            if (!compatible) {
                MinecraftLabel msg = new MinecraftLabel("no compatible server (paper/purpur/spigot/bukkit)", 2);
                msg.setPixelColor(MinecraftTheme.TEXT_MUTED);
                msg.setShadow(false);
                JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT));
                center.setOpaque(false);
                center.add(msg);
                panel.add(center, BorderLayout.CENTER);
                return;
            }
            panel.add(buildList(), BorderLayout.CENTER);
        }

        private static boolean isPluginServer(AppConfig config) {
            String cmd = (config.serverCommand() == null ? "" : config.serverCommand()).toLowerCase(Locale.ROOT);
            return cmd.contains("paper") || cmd.contains("purpur") || cmd.contains("spigot") || cmd.contains("bukkit");
        }

        private JComponent buildList() {
            JPanel list = new JPanel();
            list.setOpaque(false);
            list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));

            for (PluginEntry entry : scan()) {
                list.add(new PluginRow(pluginsDir, entry, this::refresh));
                list.add(javax.swing.Box.createVerticalStrut(MinecraftTheme.scale(4)));
            }
            list.add(javax.swing.Box.createVerticalGlue());

            JScrollPane scroll = new JScrollPane(list);
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
            scroll.setBorder(null);
            scroll.getVerticalScrollBar().setUI(new MinecraftScrollBarUI());
            scroll.getVerticalScrollBar().setUnitIncrement(MinecraftTheme.scale(16));
            return scroll;
        }

        private List<PluginEntry> scan() {
            List<Path> dirs = new ArrayList<>();
            List<Path> jars = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir)) {
                for (Path p : stream) {
                    if (Files.isDirectory(p)) {
                        dirs.add(p);
                    } else if (Files.isRegularFile(p)) {
                        String lower = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (lower.endsWith(".jar") || lower.endsWith(".jarx")) jars.add(p);
                    }
                }
            } catch (IOException ignored) {
            }
            // Track directories already claimed so two plugins don't fight over the same folder.
            Set<Path> claimed = new java.util.HashSet<>();
            List<PluginEntry> out = new ArrayList<>();
            for (Path jar : jars) {
                String name = jar.getFileName().toString();
                boolean disabled = name.toLowerCase(Locale.ROOT).endsWith(".jarx");
                String stem = disabled
                        ? name.substring(0, name.length() - 5)
                        : name.substring(0, name.length() - 4);
                String canonical = readPluginYmlName(jar).orElse(stem);
                Path configDir = matchConfigDir(canonical, stem, dirs, claimed);
                if (configDir != null) claimed.add(configDir);
                out.add(new PluginEntry(jar, stem, disabled, configDir));
            }
            out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
            return out;
        }

        /**
         * Multi-pass match: canonical plugin name from plugin.yml first, then
         * normalized exact, prefix, contains, and finally a similarity-best match
         * across remaining directories.
         */
        private Path matchConfigDir(String canonical, String stem, List<Path> dirs, Set<Path> claimed) {
            List<String> candidates = List.of(canonical, stripVersion(canonical), stripVersion(stem), stem);

            // Pass 1: normalized exact match against any candidate.
            for (String cand : candidates) {
                String norm = normalize(cand);
                if (norm.isEmpty()) continue;
                for (Path dir : dirs) {
                    if (claimed.contains(dir)) continue;
                    if (normalize(dir.getFileName().toString()).equals(norm)) return dir;
                }
            }
            // Pass 2: prefix either way (handles "FastAsyncWorldEdit" vs "FastAsyncWorldEditCore").
            for (String cand : candidates) {
                String norm = normalize(cand);
                if (norm.length() < 3) continue;
                for (Path dir : dirs) {
                    if (claimed.contains(dir)) continue;
                    String dirNorm = normalize(dir.getFileName().toString());
                    if (dirNorm.startsWith(norm) || norm.startsWith(dirNorm)) return dir;
                }
            }
            // Pass 3: containment.
            for (String cand : candidates) {
                String norm = normalize(cand);
                if (norm.length() < 3) continue;
                for (Path dir : dirs) {
                    if (claimed.contains(dir)) continue;
                    String dirNorm = normalize(dir.getFileName().toString());
                    if (dirNorm.contains(norm) || norm.contains(dirNorm)) return dir;
                }
            }
            // Pass 4: best Levenshtein similarity above 0.65.
            Path best = null;
            double bestScore = 0.65;
            for (String cand : candidates) {
                String norm = normalize(cand);
                if (norm.length() < 3) continue;
                for (Path dir : dirs) {
                    if (claimed.contains(dir)) continue;
                    String dirNorm = normalize(dir.getFileName().toString());
                    double score = similarity(norm, dirNorm);
                    if (score > bestScore) {
                        bestScore = score;
                        best = dir;
                    }
                }
            }
            return best;
        }

        private static java.util.Optional<String> readPluginYmlName(Path jar) {
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar.toFile())) {
                java.util.zip.ZipEntry entry = zip.getEntry("plugin.yml");
                if (entry == null) entry = zip.getEntry("paper-plugin.yml");
                if (entry == null) entry = zip.getEntry("bungee.yml");
                if (entry == null) return java.util.Optional.empty();
                try (java.io.BufferedReader r = new java.io.BufferedReader(
                        new java.io.InputStreamReader(zip.getInputStream(entry), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("name:")) {
                            String value = trimmed.substring(5).trim();
                            // strip quotes and inline comments
                            int hash = value.indexOf('#');
                            if (hash >= 0) value = value.substring(0, hash).trim();
                            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                                (value.startsWith("'") && value.endsWith("'"))) {
                                value = value.substring(1, value.length() - 1);
                            }
                            return value.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(value);
                        }
                    }
                }
            } catch (IOException ignored) {
            }
            return java.util.Optional.empty();
        }

        private static String normalize(String s) {
            if (s == null) return "";
            return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        }

        private static String stripVersion(String stem) {
            return stem.replaceAll("[-_ ]?[vV]?\\d[\\d.\\-_]*([-_]?(SNAPSHOT|RELEASE|BETA|ALPHA|RC\\d*))?$", "")
                    .replaceAll("[-_ ]+$", "");
        }

        private static double similarity(String a, String b) {
            if (a.isEmpty() && b.isEmpty()) return 1.0;
            int distance = levenshtein(a, b);
            int max = Math.max(a.length(), b.length());
            return max == 0 ? 1.0 : 1.0 - ((double) distance / max);
        }

        private static int levenshtein(String a, String b) {
            int n = a.length(), m = b.length();
            if (n == 0) return m;
            if (m == 0) return n;
            int[] prev = new int[m + 1];
            int[] curr = new int[m + 1];
            for (int j = 0; j <= m; j++) prev[j] = j;
            for (int i = 1; i <= n; i++) {
                curr[0] = i;
                for (int j = 1; j <= m; j++) {
                    int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                    curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                }
                int[] tmp = prev; prev = curr; curr = tmp;
            }
            return prev[m];
        }

        private void refresh() {
            panel.removeAll();
            MinecraftLabel header = new MinecraftLabel("Plugins", 4);
            header.setSmallCaps(true);
            JPanel head = new JPanel();
            head.setOpaque(false);
            head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
            head.add(header);
            head.setBorder(new EmptyBorder(0, 0, MinecraftTheme.scale(14), 0));
            panel.add(head, BorderLayout.NORTH);
            panel.add(buildList(), BorderLayout.CENTER);
            panel.revalidate();
            panel.repaint();
        }

        @Override public String id() { return "plugins"; }
        @Override public String title() { return "Plugins"; }
        @Override public JComponent component() { return panel; }
        @Override public boolean supportsApply() { return false; }

        @Override
        public AppConfig apply(AppConfig current) {
            return current;
        }

        private record PluginEntry(Path jar, String name, boolean disabled, Path configDir) {}
    }

    // ── Plugin row ─────────────────────────────────────────────────────────
    private static final class PluginRow extends JPanel {
        private final Path pluginsDir;
        private PluginsTab.PluginEntry entry;
        private final Runnable onChange;
        private final JPanel ymlPanel = new JPanel();
        private boolean expanded;
        private final JLabel arrow = new JLabel("▶");
        private final MinecraftButton toggle;

        PluginRow(Path pluginsDir, PluginsTab.PluginEntry entry, Runnable onChange) {
            super(new BorderLayout());
            this.pluginsDir = pluginsDir;
            this.entry = entry;
            this.onChange = onChange;
            setOpaque(false);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 1, 1, 1, MinecraftTheme.BORDER_LIGHT),
                    new EmptyBorder(MinecraftTheme.scale(6), MinecraftTheme.scale(8), MinecraftTheme.scale(6), MinecraftTheme.scale(8))));

            JPanel header = new JPanel(new BorderLayout(MinecraftTheme.scale(8), 0));
            header.setOpaque(false);

            arrow.setForeground(MinecraftTheme.TEXT_MUTED);
            arrow.setFont(arrow.getFont().deriveFont(Font.BOLD, MinecraftTheme.scale(14)));
            arrow.setHorizontalAlignment(SwingConstants.CENTER);
            arrow.setPreferredSize(new Dimension(MinecraftTheme.scale(18), MinecraftTheme.scale(18)));
            arrow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            arrow.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { toggleExpanded(); }
            });

            MinecraftLabel name = new MinecraftLabel(entry.name() + (entry.disabled() ? " (disabled)" : ""), 2);
            name.setPixelColor(entry.disabled() ? MinecraftTheme.TEXT_MUTED : MinecraftTheme.PANEL_TEXT);

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, MinecraftTheme.scale(8), 0));
            left.setOpaque(false);
            left.add(arrow);
            left.add(name);

            this.toggle = new MinecraftButton(entry.disabled() ? "Enable" : "Disable", CONTROL_FONT_SCALE);
            toggle.addActionListener(e -> doToggle());

            header.add(left, BorderLayout.CENTER);
            header.add(toggle, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);

            ymlPanel.setOpaque(false);
            ymlPanel.setLayout(new BoxLayout(ymlPanel, BoxLayout.Y_AXIS));
            ymlPanel.setBorder(new EmptyBorder(MinecraftTheme.scale(6), MinecraftTheme.scale(28), 0, 0));
            ymlPanel.setVisible(false);
            add(ymlPanel, BorderLayout.CENTER);
            populateYmls();
        }

        private void toggleExpanded() {
            expanded = !expanded;
            arrow.setText(expanded ? "▼" : "▶");
            ymlPanel.setVisible(expanded);
            revalidate();
            repaint();
        }

        private void populateYmls() {
            ymlPanel.removeAll();
            if (entry.configDir() == null) {
                MinecraftLabel none = new MinecraftLabel("no config folder", 1);
                none.setPixelColor(MinecraftTheme.TEXT_MUTED);
                none.setShadow(false);
                ymlPanel.add(none);
                return;
            }
            List<Path> ymls = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(entry.configDir())) {
                for (Path p : stream) {
                    if (!Files.isRegularFile(p)) continue;
                    String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (n.endsWith(".yml") || n.endsWith(".yaml")) ymls.add(p);
                }
            } catch (IOException ignored) {
            }
            ymls.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
            if (ymls.isEmpty()) {
                MinecraftLabel none = new MinecraftLabel("no .yml files", 1);
                none.setPixelColor(MinecraftTheme.TEXT_MUTED);
                none.setShadow(false);
                ymlPanel.add(none);
                return;
            }
            for (Path yml : ymls) {
                ymlPanel.add(buildYmlLink(yml));
                ymlPanel.add(javax.swing.Box.createVerticalStrut(MinecraftTheme.scale(2)));
            }
        }

        private JComponent buildYmlLink(Path yml) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, MinecraftTheme.scale(6), 0));
            row.setOpaque(false);
            MinecraftLinkButton link = new MinecraftLinkButton(yml.getFileName().toString());
            link.addActionListener(e -> openInEditor(yml));
            row.add(link);
            return row;
        }

        private static void openInEditor(Path file) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop d = Desktop.getDesktop();
                    if (d.isSupported(Desktop.Action.EDIT)) {
                        d.edit(file.toFile());
                        return;
                    }
                    if (d.isSupported(Desktop.Action.OPEN)) {
                        d.open(file.toFile());
                        return;
                    }
                }
                JOptionPane.showMessageDialog(null, "No editor available for " + file, "Open failed", JOptionPane.WARNING_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(null, "Failed to open: " + ex.getMessage(), "Open failed", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void doToggle() {
            try {
                Path src = entry.jar();
                String name = src.getFileName().toString();
                Path dest;
                if (entry.disabled()) {
                    String stem = name.substring(0, name.length() - 5);
                    dest = pluginsDir.resolve(stem + ".jar");
                } else {
                    String stem = name.substring(0, name.length() - 4);
                    dest = pluginsDir.resolve(stem + ".jarX");
                }
                Files.move(src, dest, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to toggle: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            onChange.run();
        }
    }

    // ── Git Sync tab ───────────────────────────────────────────────────────
    private static final class GitSyncTab implements Tab {
        private final JPanel panel = new JPanel(new BorderLayout());
        private final AppConfig config;
        private final JCheckBox enabledBox = new JCheckBox();
        private final JTextField repoField = new JTextField(28);
        private Path selectedRepo;

        GitSyncTab(AppConfig config) {
            this.config = config;
            panel.setOpaque(false);

            config.gitRepositoryPath().ifPresent(p -> selectedRepo = p);
            if (selectedRepo == null) {
                autoDetectRepo().ifPresent(p -> selectedRepo = p);
            }
            enabledBox.setOpaque(false);
            enabledBox.setForeground(MinecraftTheme.PANEL_TEXT);
            enabledBox.setSelected(config.gitSyncEnabled());

            repoField.setEditable(false);
            repoField.setOpaque(true);
            repoField.setBackground(new Color(0x1B1B1B));
            repoField.setForeground(MinecraftTheme.PANEL_TEXT);
            repoField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 1, 1, 1, MinecraftTheme.BORDER_LIGHT),
                    new EmptyBorder(4, 6, 4, 6)));
            refreshRepoField();

            MinecraftButton pick = new MinecraftButton("Pick repo", CONTROL_FONT_SCALE);
            pick.addActionListener(e -> pickRepo());

            MinecraftLabel header = new MinecraftLabel("Git Sync", 4);
            header.setSmallCaps(true);
            MinecraftLabel note = new MinecraftLabel(
                    "opped players can type !git pull (or !git pull reload) in chat to sync", 1);
            note.setPixelColor(MinecraftTheme.TEXT_MUTED);
            note.setShadow(false);
            JPanel head = new JPanel();
            head.setOpaque(false);
            head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
            head.add(header);
            head.add(javax.swing.Box.createVerticalStrut(MinecraftTheme.scale(8)));
            head.add(note);
            head.setBorder(new EmptyBorder(0, 0, MinecraftTheme.scale(16), 0));

            JPanel form = new JPanel(new GridBagLayout());
            form.setOpaque(false);
            GridBagConstraints g = new GridBagConstraints();
            g.gridx = 0; g.gridy = 0;
            g.anchor = GridBagConstraints.WEST;
            g.fill = GridBagConstraints.HORIZONTAL;
            g.insets = new Insets(0, 0, MinecraftTheme.scale(8), MinecraftTheme.scale(10));

            MinecraftLabel enableLabel = new MinecraftLabel("Enable chat sync", 2);
            enableLabel.setSmallCaps(true);
            g.gridx = 0; g.weightx = 0;
            form.add(enableLabel, g);
            g.gridx = 1; g.weightx = 1;
            form.add(enabledBox, g);
            g.gridy++;

            MinecraftLabel repoLabel = new MinecraftLabel("Repository", 2);
            repoLabel.setSmallCaps(true);
            g.gridx = 0; g.weightx = 0;
            form.add(repoLabel, g);
            g.gridx = 1; g.weightx = 1;
            form.add(repoField, g);
            g.gridx = 2; g.weightx = 0;
            form.add(pick, g);

            panel.add(head, BorderLayout.NORTH);
            panel.add(form, BorderLayout.CENTER);
        }

        private void refreshRepoField() {
            repoField.setText(selectedRepo == null ? "no folder selected" : selectedRepo.toString());
        }

        /** Walk the working directory (depth-limited) for a single {@code .git} folder. */
        private Optional<Path> autoDetectRepo() {
            Path root = config.workingDirectory();
            if (!Files.isDirectory(root)) return Optional.empty();
            try (java.util.stream.Stream<Path> stream = Files.walk(root, 4)) {
                List<Path> repos = stream
                        .filter(p -> p.getFileName() != null && ".git".equals(p.getFileName().toString()))
                        .filter(Files::isDirectory)
                        .map(Path::getParent)
                        .toList();
                return repos.size() == 1 ? Optional.of(repos.get(0)) : Optional.empty();
            } catch (IOException ex) {
                return Optional.empty();
            }
        }

        private void pickRepo() {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Select the git repository folder");
            chooser.setCurrentDirectory((selectedRepo != null ? selectedRepo : config.workingDirectory()).toFile());
            if (chooser.showOpenDialog(panel) != JFileChooser.APPROVE_OPTION) return;

            Path chosen = chooser.getSelectedFile().toPath();
            // Allow picking the .git folder itself.
            if (".git".equals(chosen.getFileName().toString())) chosen = chosen.getParent();
            if (chosen == null || !Files.isDirectory(chosen.resolve(".git"))) {
                JOptionPane.showMessageDialog(panel,
                        "That folder does not contain a .git directory.",
                        "Not a git repository", JOptionPane.WARNING_MESSAGE);
                return;
            }
            selectedRepo = chosen.toAbsolutePath().normalize();
            refreshRepoField();
        }

        @Override public String id() { return "gitsync"; }
        @Override public String title() { return "Git Sync"; }
        @Override public JComponent component() { return panel; }

        @Override
        public AppConfig apply(AppConfig current) {
            boolean enabled = enabledBox.isSelected();
            if (enabled) {
                if (selectedRepo == null) {
                    throw new IllegalArgumentException("Select a repository folder before enabling Git Sync.");
                }
                if (!Files.isDirectory(selectedRepo.resolve(".git"))) {
                    throw new IllegalArgumentException("The selected folder no longer contains a .git directory.");
                }
            }
            return current.saveGitSync(enabled, selectedRepo);
        }
    }

    // ── Property type registry ─────────────────────────────────────────────
    private enum PropKind { BOOL, INT, ENUM, STRING }

    private static final class PropType {
        private static final Map<String, PropType> REGISTRY = buildRegistry();
        final PropKind kind;
        final int minInt;
        final int maxInt;
        final String[] options;

        private PropType(PropKind kind, int minInt, int maxInt, String[] options) {
            this.kind = kind;
            this.minInt = minInt;
            this.maxInt = maxInt;
            this.options = options;
        }

        static PropType forKey(String key) {
            return REGISTRY.getOrDefault(key, new PropType(PropKind.STRING, 0, 0, null));
        }

        private static PropType bool() { return new PropType(PropKind.BOOL, 0, 0, null); }
        private static PropType integer(int min, int max) { return new PropType(PropKind.INT, min, max, null); }
        private static PropType enumOf(String... options) { return new PropType(PropKind.ENUM, 0, 0, options); }

        private static Map<String, PropType> buildRegistry() {
            Map<String, PropType> m = new java.util.HashMap<>();
            // Booleans
            for (String key : Arrays.asList(
                    "allow-flight", "allow-nether", "broadcast-console-to-ops", "broadcast-rcon-to-ops",
                    "enable-command-block", "enable-jmx-monitoring", "enable-query", "enable-rcon",
                    "enable-status", "enforce-secure-profile", "enforce-whitelist", "force-gamemode",
                    "generate-structures", "hardcore", "hide-online-players", "online-mode",
                    "prevent-proxy-connections", "pvp", "require-resource-pack", "snooper-enabled",
                    "spawn-monsters", "spawn-animals", "spawn-npcs", "sync-chunk-writes",
                    "use-native-transport", "white-list", "accepts-transfers", "log-ips")) {
                m.put(key, bool());
            }
            // Integers (with reasonable bounds)
            m.put("max-players", integer(0, 10000));
            m.put("view-distance", integer(2, 32));
            m.put("simulation-distance", integer(2, 32));
            m.put("server-port", integer(1, 65535));
            m.put("query.port", integer(1, 65535));
            m.put("rcon.port", integer(1, 65535));
            m.put("spawn-protection", integer(0, 10000));
            m.put("op-permission-level", integer(0, 4));
            m.put("function-permission-level", integer(0, 4));
            m.put("player-idle-timeout", integer(0, 1000000));
            m.put("entity-broadcast-range-percentage", integer(0, 500));
            m.put("max-tick-time", integer(-1, Integer.MAX_VALUE));
            m.put("max-world-size", integer(1, 29999984));
            m.put("network-compression-threshold", integer(-1, 1000000));
            m.put("max-chained-neighbor-updates", integer(0, 1000000));
            m.put("rate-limit", integer(0, 1000000));
            // Enums
            m.put("difficulty", enumOf("peaceful", "easy", "normal", "hard"));
            m.put("gamemode", enumOf("survival", "creative", "adventure", "spectator"));
            m.put("level-type", enumOf("minecraft:normal", "minecraft:flat", "minecraft:large_biomes",
                    "minecraft:amplified", "minecraft:single_biome_surface"));
            return m;
        }
    }
}
