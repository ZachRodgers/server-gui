package com.zach.minecraft.servergui.ui;

import com.zach.minecraft.servergui.config.AppConfig;
import com.zach.minecraft.servergui.config.ServerProperties;
import com.zach.minecraft.servergui.model.HeapSample;
import com.zach.minecraft.servergui.model.LogEntry;
import com.zach.minecraft.servergui.model.LogEntry.Category;
import com.zach.minecraft.servergui.model.LogEntry.Level;
import com.zach.minecraft.servergui.service.ServerController;
import com.zach.minecraft.servergui.service.ServerController.ServerStatus;
import com.zach.minecraft.servergui.util.AnsiUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.KeyboardFocusManager;
import java.awt.Taskbar;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.imageio.ImageIO;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

public final class MainFrame extends JFrame {
    private static final int CONTROL_FONT_SCALE = 2;
    private static final double[] UI_ZOOM_STEPS = {0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0};
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern OPS_NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([A-Za-z0-9_]{3,16})\"");

    private final ServerController controller;
    private final LogTableModel logModel = new LogTableModel();
    private final DefaultListModel<String> playerModel = new DefaultListModel<>();

    private final Set<Level> visibleLevels = EnumSet.allOf(Level.class);
    private final Set<Category> visibleCategories = EnumSet.allOf(Category.class);

    private JTable logTable;
    private JScrollPane logScrollPane;
    private TableRowSorter<LogTableModel> logSorter;
    private AppConfig config;
    private JSplitPane mainSplitPane;
    private JSplitPane rightTopSplitPane;
    private JSplitPane rightBottomSplitPane;
    private JPanel logToolbarPanel;
    private MinecraftFilterToggleButton logToolbarToggleButton;
    private boolean logToolbarVisible;
    private boolean autoScrollEnabled = true;
    private boolean internalScrollChange;
    private boolean manualScrollInProgress;
    private ServerStatus currentStatus = ServerStatus.OFFLINE;

    private final MinecraftTextField searchField = new MinecraftTextField("Search", 26, CONTROL_FONT_SCALE);
    private final MinecraftTextField commandField = new MinecraftTextField("Enter command", 32, CONTROL_FONT_SCALE);
    private final MinecraftLabel statusLabel = new MinecraftLabel("Offline", CONTROL_FONT_SCALE);
    private final MotdLabel titleLabel = new MotdLabel(3.5);
    private final ServerIconView serverIconView = new ServerIconView();
    private final MinecraftButton settingsButton = new MinecraftButton("Settings", CONTROL_FONT_SCALE);

    private final MinecraftButton startButton = new MinecraftButton("Start", CONTROL_FONT_SCALE);
    private final MinecraftButton stopButton = new MinecraftButton("Stop", CONTROL_FONT_SCALE);
    private final MinecraftButton restartButton = new MinecraftButton("Restart", CONTROL_FONT_SCALE);
    private final MinecraftButton killButton = new MinecraftButton("Force Kill", CONTROL_FONT_SCALE);

    private final ChartPanel tpsChart = new ChartPanel("TPS", "", MinecraftTheme.SUCCESS);
    private final ChartPanel heapChart = new ChartPanel("Memory", "", MinecraftTheme.SUCCESS);

    private final List<String> commandHistory = new ArrayList<>();
    private int commandHistoryIndex = -1;
    private String commandDraft = "";
    private int uiZoomIndex = 2;

    private int logFontSize = 16;

    public MainFrame(AppConfig config) {
        super(config.appTitle());
        this.config = config;
        this.controller = new ServerController(config);
        titleLabel.setMotd(resolveServerMotd(config));
        serverIconView.setIcon(loadServerIcon(config));

        patchDefaults();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1240, 800));
        setLocationByPlatform(true);
        installWindowIcons();
        setContentPane(buildUi(config));
        attachEvents();
        installZoomKeys();
    }

    public void autoStart() {
        autoScrollEnabled = true;
        controller.start();
    }

    private JComponent buildUi(AppConfig config) {
        JPanel root = new JPanel(new BorderLayout(0, 10)) {
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D g2 = (Graphics2D) graphics.create();
                MinecraftTheme.paintTiledBackground(g2, MinecraftTheme.LIST_BG, 0, 0, getWidth(), getHeight(), 1f);
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(MinecraftTheme.scale(12), MinecraftTheme.scale(12), MinecraftTheme.scale(12), MinecraftTheme.scale(12)));
        root.add(buildHeader(config), BorderLayout.NORTH);
        root.add(buildCenter(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        return root;
    }

    private JComponent buildHeader(AppConfig config) {
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        statusLabel.setSmallCaps(true);
        settingsButton.addActionListener(e -> openSettingsDialog());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, MinecraftTheme.scale(12), 0));
        left.setOpaque(false);
        left.add(serverIconView);
        left.add(titleLabel);
        left.add(buildStatusPanel());

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, MinecraftTheme.scale(8), 0));
        controls.setOpaque(false);
        controls.add(startButton);
        controls.add(stopButton);
        controls.add(restartButton);
        controls.add(killButton);
        controls.add(settingsButton);

        top.add(left, BorderLayout.WEST);
        top.add(controls, BorderLayout.EAST);
        return top;
    }

    private String resolveServerMotd(AppConfig config) {
        if (config.mockMode()) return config.appTitle();
        Path props = config.workingDirectory().resolve("server.properties");
        return ServerProperties.readMotd(props)
                .filter(s -> !s.isBlank())
                .orElse(config.appTitle());
    }

    private BufferedImage loadServerIcon(AppConfig config) {
        Path icon = config.workingDirectory().resolve("server-icon.png");
        if (Files.exists(icon)) {
            try (InputStream in = Files.newInputStream(icon)) {
                BufferedImage image = ImageIO.read(in);
                if (image != null) return image;
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private void refreshServerHeader() {
        titleLabel.setMotd(resolveServerMotd(config));
        serverIconView.setIcon(loadServerIcon(config));
    }

    private JComponent buildStatusPanel() {
        MinecraftPanel panel = new MinecraftPanel(true, 0.92f);
        panel.setBorder(new EmptyBorder(MinecraftTheme.scale(4), MinecraftTheme.scale(8), MinecraftTheme.scale(4), MinecraftTheme.scale(8)));
        panel.add(statusLabel, BorderLayout.CENTER);
        return panel;
    }

    private Component buildCenter() {
        buildLogTable();

        JComponent left = buildLogArea();

        JComponent players = buildPlayersCard();
        updateUiScale();
        rightBottomSplitPane = buildSplitPane(JSplitPane.VERTICAL_SPLIT, tpsChart, heapChart, 0.5);
        rightTopSplitPane = buildSplitPane(JSplitPane.VERTICAL_SPLIT, players, rightBottomSplitPane, 0.45);
        mainSplitPane = buildSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, rightTopSplitPane, 0.73);
        return mainSplitPane;
    }

    private void buildLogTable() {
        logTable = new JTable(logModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component component = super.prepareRenderer(renderer, row, column);
                int preferred = component.getPreferredSize().height;
                if (getRowHeight(row) != preferred) {
                    setRowHeight(row, preferred);
                }
                return component;
            }
        };
        logTable.setOpaque(false);
        logTable.setFillsViewportHeight(true);
        logTable.setShowHorizontalLines(false);
        logTable.setShowVerticalLines(false);
        logTable.setIntercellSpacing(new Dimension(0, 0));
        logTable.setRowHeight(16);
        logTable.setTableHeader(null);
        logTable.setDefaultRenderer(Object.class, new PixelLogCellRenderer());
        logTable.setSelectionBackground(MinecraftTheme.SELECTION);
        logTable.setSelectionForeground(MinecraftTheme.PANEL_TEXT);
        logTable.setRowMargin(0);
        logTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        TableColumnModel cols = logTable.getColumnModel();
        cols.getColumn(0).setMinWidth(MinecraftTheme.scale(104));
        cols.getColumn(0).setMaxWidth(MinecraftTheme.scale(110));
        cols.getColumn(1).setMinWidth(MinecraftTheme.scale(86));
        cols.getColumn(1).setMaxWidth(MinecraftTheme.scale(94));

        logSorter = new TableRowSorter<>(logModel);
        for (int i = 0; i < logModel.getColumnCount(); i++) logSorter.setSortable(i, false);
        logTable.setRowSorter(logSorter);
        searchField.getDocument().addDocumentListener(SimpleDocumentListener.onChange(this::applyFilters));

        logScrollPane = new JScrollPane(logTable);
        styleScrollPane(logScrollPane);
        logScrollPane.getViewport().setOpaque(false);
        logScrollPane.setViewportView(logTable);
        JScrollBar bar = logScrollPane.getVerticalScrollBar();
        logScrollPane.addMouseWheelListener(event -> {
            if (currentStatus != ServerStatus.ONLINE) return;
            SwingUtilities.invokeLater(() -> autoScrollEnabled = isAtBottom(bar));
        });
        bar.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (currentStatus == ServerStatus.ONLINE) {
                    manualScrollInProgress = true;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (currentStatus == ServerStatus.ONLINE) {
                    manualScrollInProgress = false;
                    autoScrollEnabled = isAtBottom(bar);
                }
            }
        });
        bar.addAdjustmentListener(event -> {
            if (internalScrollChange) return;
            if (currentStatus != ServerStatus.ONLINE) {
                autoScrollEnabled = true;
                return;
            }
            if (isAtBottom(bar)) {
                autoScrollEnabled = true;
            } else if (manualScrollInProgress) {
                autoScrollEnabled = false;
            }
        });
    }

    private void styleScrollPane(JScrollPane scrollPane) {
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        int stroke = Math.max(2, MinecraftTheme.scale(2));
        scrollPane.setBorder(BorderFactory.createMatteBorder(stroke, stroke, stroke, stroke, MinecraftTheme.BORDER_LIGHT));
        scrollPane.getVerticalScrollBar().setUI(new MinecraftScrollBarUI());
        scrollPane.getHorizontalScrollBar().setUI(new MinecraftScrollBarUI());
        scrollPane.getVerticalScrollBar().setUnitIncrement(MinecraftTheme.scale(16));
    }

    private JComponent buildLogToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, MinecraftTheme.scale(8), 0));
        bar.setOpaque(false);
        bar.add(searchField);
        bar.add(buildFilterButton());
        return bar;
    }

    private JComponent buildLogArea() {
        logToolbarPanel = (JPanel) buildLogToolbar();
        logToolbarPanel.setVisible(logToolbarVisible);
        logToolbarToggleButton = new MinecraftFilterToggleButton();
        logToolbarToggleButton.addActionListener(e -> toggleLogToolbar());

        JLayeredPane layeredPane = new JLayeredPane() {
            @Override
            public Dimension getPreferredSize() {
                return logScrollPane.getPreferredSize();
            }

            @Override
            public void doLayout() {
                int width = getWidth();
                int height = getHeight();
                int inset = MinecraftTheme.scale(4);
                int toolbarGap = MinecraftTheme.scale(6);
                int buttonWidth = logToolbarToggleButton.getPreferredSize().width;
                int buttonHeight = logToolbarToggleButton.getPreferredSize().height;
                int buttonX = width - buttonWidth - inset;
                int buttonY = inset;
                logToolbarToggleButton.setBounds(buttonX, buttonY, buttonWidth, buttonHeight);

                int toolbarHeight = logToolbarVisible ? logToolbarPanel.getPreferredSize().height : 0;
                if (logToolbarVisible) {
                    logToolbarPanel.setBounds(inset, inset, Math.max(0, width - buttonWidth - (inset * 3)), toolbarHeight);
                    logScrollPane.setBounds(0, toolbarHeight + toolbarGap + inset, width, Math.max(0, height - toolbarHeight - toolbarGap - inset));
                } else {
                    logToolbarPanel.setBounds(inset, inset, Math.max(0, width - buttonWidth - (inset * 3)), 0);
                    logScrollPane.setBounds(0, 0, width, height);
                }
            }
        };
        layeredPane.setOpaque(false);
        layeredPane.add(logScrollPane, Integer.valueOf(0));
        layeredPane.add(logToolbarPanel, Integer.valueOf(1));
        layeredPane.add(logToolbarToggleButton, Integer.valueOf(2));
        return layeredPane;
    }

    private void toggleLogToolbar() {
        logToolbarVisible = !logToolbarVisible;
        if (logToolbarPanel != null) logToolbarPanel.setVisible(logToolbarVisible);
        if (logToolbarPanel != null) {
            logToolbarPanel.revalidate();
            logToolbarPanel.repaint();
        }
        if (logToolbarToggleButton != null) {
            logToolbarToggleButton.repaint();
        }
        if (logScrollPane != null && logScrollPane.getParent() != null) {
            logScrollPane.getParent().revalidate();
            logScrollPane.getParent().repaint();
        }
    }

    private MinecraftButton buildFilterButton() {
        MinecraftButton button = new MinecraftButton("Filter", CONTROL_FONT_SCALE);

        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, MinecraftTheme.BORDER_LIGHT));
        popup.setBackground(MinecraftTheme.BG);

        popup.add(checkItem("Info", Level.INFO, null, MinecraftTheme.INFO));
        popup.add(checkItem("Warn", Level.WARN, null, MinecraftTheme.WARN));
        popup.add(checkItem("Error", Level.ERROR, null, MinecraftTheme.ERROR));
        popup.add(new JSeparator());
        popup.add(checkItem("Chat", null, Category.CHAT, MinecraftTheme.CHAT));
        popup.add(checkItem("Commands", null, Category.COMMAND, MinecraftTheme.COMMAND));
        popup.add(checkItem("System", null, Category.SYSTEM, MinecraftTheme.TEXT_MUTED));

        button.addActionListener(e -> popup.show(button, 0, button.getHeight()));
        return button;
    }

    private JCheckBoxMenuItem checkItem(String text, Level level, Category category, Color color) {
        JCheckBoxMenuItem item = new MinecraftCheckBoxMenuItem(text, true, color);
        item.setForeground(color);
        item.setBackground(MinecraftTheme.BG);
        item.addActionListener(e -> {
            if (level != null) toggle(visibleLevels, level, item.isSelected());
            if (category != null) toggle(visibleCategories, category, item.isSelected());
            applyFilters();
        });
        return item;
    }

    private static <T> void toggle(Set<T> set, T value, boolean add) {
        if (add) set.add(value);
        else set.remove(value);
    }

    private JComponent buildPlayersCard() {
        MinecraftPanel panel = new MinecraftPanel(true, 0.95f);
        panel.setBorder(new EmptyBorder(MinecraftTheme.scale(10), MinecraftTheme.scale(10), MinecraftTheme.scale(10), MinecraftTheme.scale(10)));
        JPanel content = new JPanel(new BorderLayout(0, MinecraftTheme.scale(8)));
        content.setOpaque(false);

        MinecraftLabel title = new MinecraftLabel("Players Online", 2);
        title.setSmallCaps(true);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JList<String> list = new JList<>(playerModel);
        list.setOpaque(false);
        list.setCellRenderer(new PlayerCellRenderer());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showPlayerMenu(list, e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showPlayerMenu(list, e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) showPlayerMenu(list, e);
            }
        });

        JScrollPane scroller = new JScrollPane(list);
        scroller.setOpaque(false);
        scroller.getViewport().setOpaque(false);
        scroller.setBorder(null);
        scroller.getVerticalScrollBar().setUI(new MinecraftScrollBarUI());
        scroller.getHorizontalScrollBar().setUI(new MinecraftScrollBarUI());
        scroller.getVerticalScrollBar().setUnitIncrement(MinecraftTheme.scale(16));
        scroller.setPreferredSize(new Dimension(MinecraftTheme.scale(300), MinecraftTheme.scale(220)));
        content.add(title, BorderLayout.NORTH);
        content.add(scroller, BorderLayout.CENTER);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private void showPlayerMenu(JList<String> list, MouseEvent e) {
        int index = list.locationToIndex(e.getPoint());
        if (index < 0 || !list.getCellBounds(index, index).contains(e.getPoint())) return;

        String playerName = playerModel.getElementAt(index);
        if (!PLAYER_NAME_PATTERN.matcher(playerName).matches()) return;

        list.setSelectedIndex(index);
        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, MinecraftTheme.BORDER_LIGHT));
        popup.setBackground(MinecraftTheme.BG);
        if (isPlayerOpped(playerName)) {
            popup.add(playerActionItem("Deop", "deop", playerName));
        } else {
            popup.add(playerActionItem("Op", "op", playerName));
        }
        popup.add(new JSeparator());
        popup.add(playerActionItem("Kick", "kick", playerName));
        popup.add(playerActionItem("Ban", "ban", playerName));
        popup.show(list, e.getX(), e.getY());
    }

    private JMenuItem playerActionItem(String label, String command, String playerName) {
        JMenuItem item = new MinecraftMenuItem(label, MinecraftTheme.PANEL_TEXT);
        item.addActionListener(e -> runPlayerAction(command, playerName));
        return item;
    }

    private void runPlayerAction(String command, String playerName) {
        if (!PLAYER_NAME_PATTERN.matcher(playerName).matches()) {
            return;
        }
        controller.sendCommand(command + " " + playerName);
    }

    private boolean isPlayerOpped(String playerName) {
        Path opsPath = config.workingDirectory().resolve("ops.json");
        if (Files.notExists(opsPath)) return false;
        try {
            String ops = Files.readString(opsPath);
            var matcher = OPS_NAME_PATTERN.matcher(ops);
            while (matcher.find()) {
                if (matcher.group(1).equalsIgnoreCase(playerName)) {
                    return true;
                }
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    private JSplitPane buildSplitPane(int orientation, Component first, Component second, double resizeWeight) {
        JSplitPane split = new JSplitPane(orientation, first, second);
        split.setResizeWeight(resizeWeight);
        split.setDividerSize(MinecraftTheme.scale(6));
        split.setOpaque(false);
        split.setBorder(null);
        split.setContinuousLayout(true);
        split.setUI(new MinecraftSplitPaneUI());
        return split;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(MinecraftTheme.scale(8), 0));
        footer.setOpaque(false);
        footer.add(commandField, BorderLayout.CENTER);
        MinecraftButton send = new MinecraftButton("Send", CONTROL_FONT_SCALE);
        send.addActionListener(e -> sendCommand());
        footer.add(send, BorderLayout.EAST);
        commandField.addActionListener(e -> sendCommand());
        installCommandHistory();
        return footer;
    }

    private void attachEvents() {
        startButton.addActionListener(e -> controller.start());
        stopButton.addActionListener(e -> controller.stop());
        restartButton.addActionListener(e -> controller.restart());
        killButton.addActionListener(e -> controller.forceKill());

        controller.onLog(entry -> SwingUtilities.invokeLater(() -> {
            logModel.add(entry);
            maybeScrollToBottom();
        }));
        controller.onPlayers(players -> SwingUtilities.invokeLater(() -> updatePlayers(players)));
        controller.onTps(value -> SwingUtilities.invokeLater(() -> tpsChart.addPoint(value, 20.0)));
        controller.onHeap(sample -> SwingUtilities.invokeLater(() -> updateHeap(sample)));
        controller.onStatus(status -> SwingUtilities.invokeLater(() -> updateStatus(status)));

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                controller.shutdown();
                dispose();
            }
        });
    }

    private void installZoomKeys() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED && e.isControlDown()) {
                int key = e.getKeyCode();
                if (key == KeyEvent.VK_EQUALS || key == KeyEvent.VK_ADD) {
                    adjustUiZoom(+1);
                    return true;
                }
                if (key == KeyEvent.VK_MINUS || key == KeyEvent.VK_SUBTRACT) {
                    adjustUiZoom(-1);
                    return true;
                }
            }
            return false;
        });
    }

    private void installCommandHistory() {
        commandField.getInputMap().put(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "history-up");
        commandField.getActionMap().put("history-up", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stepCommandHistory(-1);
            }
        });
        commandField.getInputMap().put(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "history-down");
        commandField.getActionMap().put("history-down", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stepCommandHistory(1);
            }
        });
    }

    private void sendCommand() {
        String command = commandField.getText().trim();
        if (command.isEmpty()) return;
        controller.sendCommand(command);
        if (commandHistory.isEmpty() || !commandHistory.get(commandHistory.size() - 1).equals(command)) {
            commandHistory.add(command);
        }
        commandHistoryIndex = commandHistory.size();
        commandDraft = "";
        commandField.setText("");
    }

    private void updatePlayers(List<String> players) {
        playerModel.clear();
        players.forEach(playerModel::addElement);
    }

    private void updateHeap(HeapSample sample) { heapChart.addPoint(sample.usedMb(), sample.totalMb()); }

    private void clearRuntimePanels() {
        playerModel.clear();
        tpsChart.clear();
        heapChart.clear();
    }

    private void updateStatus(ServerStatus status) {
        currentStatus = status;
        switch (status) {
            case LOADING -> {
                autoScrollEnabled = true;
                clearRuntimePanels();
                statusLabel.setPixelText("Loading");
                statusLabel.setPixelColor(MinecraftTheme.WARN);
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                restartButton.setEnabled(false);
                killButton.setEnabled(true);
            }
            case ONLINE -> {
                autoScrollEnabled = true;
                statusLabel.setPixelText("Online");
                statusLabel.setPixelColor(MinecraftTheme.SUCCESS);
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                restartButton.setEnabled(true);
                killButton.setEnabled(true);
                scrollToBottom();
            }
            case OFFLINE -> {
                autoScrollEnabled = true;
                clearRuntimePanels();
                statusLabel.setPixelText("Offline");
                statusLabel.setPixelColor(MinecraftTheme.ERROR);
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                restartButton.setEnabled(false);
                killButton.setEnabled(false);
            }
        }
        if (status != ServerStatus.ONLINE) {
            scrollToBottom();
        }
    }

    private void openSettingsDialog() {
        SettingsDialog dialog = new SettingsDialog(this, config, this::applySettings);
        dialog.setVisible(true);
    }

    void applySettings(AppConfig updatedConfig, boolean restartServer) {
        this.config = updatedConfig;
        controller.reconfigure(updatedConfig);
        setTitle(updatedConfig.appTitle());
        refreshServerHeader();
        if (restartServer && controller.isRunning()) {
            controller.restart();
        }
    }

    private void applyFilters() {
        String search = searchField.getText().trim().toLowerCase(Locale.ROOT);
        logSorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends LogTableModel, ? extends Integer> entry) {
                int modelRow = (Integer) entry.getIdentifier();
                LogEntry logEntry = logModel.getEntry(modelRow);
                if (!visibleLevels.contains(logEntry.level())) return false;
                if (!visibleCategories.contains(logEntry.category())) return false;
                if (!search.isBlank()) {
                    String message = AnsiUtil.strip(String.valueOf(entry.getValue(2))).toLowerCase(Locale.ROOT);
                    return message.contains(search);
                }
                return true;
            }
        });
        if (logScrollPane != null && (autoScrollEnabled || isAtBottom(logScrollPane.getVerticalScrollBar()))) {
            scrollToBottom();
        }
    }

    private void maybeScrollToBottom() {
        if (logScrollPane == null) return;
        if (autoScrollEnabled) {
            scrollToBottom();
        }
    }

    private void scrollToBottom() {
        if (logTable == null || logScrollPane == null) return;
        SwingUtilities.invokeLater(() -> {
            int last = logTable.getRowCount() - 1;
            if (last >= 0) {
                internalScrollChange = true;
                logTable.scrollRectToVisible(logTable.getCellRect(last, 0, true));
                JScrollBar bar = logScrollPane.getVerticalScrollBar();
                bar.setValue(bar.getMaximum());
                autoScrollEnabled = true;
                internalScrollChange = false;
            }
        });
    }

    private boolean isAtBottom(JScrollBar bar) {
        return bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - MinecraftTheme.scale(4);
    }

    private void installWindowIcons() {
        List<Image> icons = AppIconFactory.createIcons();
        setIconImages(icons);
        if (Taskbar.isTaskbarSupported()) {
            try {
                Taskbar.getTaskbar().setIconImage(icons.get(icons.size() - 1));
            } catch (UnsupportedOperationException | SecurityException ignored) {
            }
        }
    }

    private void adjustUiZoom(int delta) {
        uiZoomIndex = Math.max(0, Math.min(UI_ZOOM_STEPS.length - 1, uiZoomIndex + delta));
        updateUiScale();
    }

    private void updateUiScale() {
        MinecraftTheme.setUiScale(UI_ZOOM_STEPS[uiZoomIndex]);
        logFontSize = Math.max(8, (int) Math.round(16 * MinecraftTheme.uiScale()));
        if (logTable != null) {
            logTable.setRowHeight(pixelScale() == 1 ? MinecraftTheme.scale(14) : pixelScale() == 2 ? MinecraftTheme.scale(22) : MinecraftTheme.scale(30));
            logTable.revalidate();
            logTable.repaint();
        }
        tpsChart.setPreferredSize(new Dimension(MinecraftTheme.scale(320), MinecraftTheme.scale(160)));
        heapChart.setPreferredSize(new Dimension(MinecraftTheme.scale(320), MinecraftTheme.scale(160)));
        tpsChart.setMaximumSize(new Dimension(Integer.MAX_VALUE, MinecraftTheme.scale(160)));
        heapChart.setMaximumSize(new Dimension(Integer.MAX_VALUE, MinecraftTheme.scale(160)));
        if (mainSplitPane != null) mainSplitPane.setDividerSize(MinecraftTheme.scale(6));
        if (rightTopSplitPane != null) rightTopSplitPane.setDividerSize(MinecraftTheme.scale(6));
        if (rightBottomSplitPane != null) rightBottomSplitPane.setDividerSize(MinecraftTheme.scale(6));
        setMinimumSize(new Dimension(MinecraftTheme.scale(1240), MinecraftTheme.scale(800)));
        if (getContentPane() instanceof JComponent component) {
            component.setBorder(new EmptyBorder(MinecraftTheme.scale(12), MinecraftTheme.scale(12), MinecraftTheme.scale(12), MinecraftTheme.scale(12)));
        }
        revalidate();
        repaint();
    }

    private void stepCommandHistory(int direction) {
        if (commandHistory.isEmpty()) return;
        if (commandHistoryIndex == commandHistory.size()) {
            commandDraft = commandField.getText();
        }
        commandHistoryIndex = Math.max(0, Math.min(commandHistory.size(), commandHistoryIndex + direction));
        if (commandHistoryIndex == commandHistory.size()) {
            commandField.setText(commandDraft);
        } else {
            commandField.setText(commandHistory.get(commandHistoryIndex));
        }
    }

    private int pixelScale() {
        return Math.max(1, Math.min(3, Math.round(logFontSize / 8f)));
    }

    private static void patchDefaults() {
        UIManager.put("PopupMenu.background", MinecraftTheme.BG);
        UIManager.put("MenuItem.background", MinecraftTheme.BG);
        UIManager.put("MenuItem.foreground", MinecraftTheme.PANEL_TEXT);
        UIManager.put("CheckBoxMenuItem.background", MinecraftTheme.BG);
        UIManager.put("CheckBoxMenuItem.foreground", MinecraftTheme.PANEL_TEXT);
        UIManager.put("ScrollBar.width", 8);
    }

    private final class PixelLogCellRenderer extends JComponent implements TableCellRenderer {
        private Object value;
        private Level level;
        private boolean selected;
        private int column;
        private List<WrappedLine> lines = List.of();

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            int modelRow = table.convertRowIndexToModel(row);
            this.value = value;
            this.level = (Level) table.getModel().getValueAt(modelRow, 1);
            this.selected = isSelected;
            this.column = column;
            if (column == 2) {
                this.lines = wrapSegments(
                        AnsiUtil.segments(String.valueOf(value), MinecraftTheme.PANEL_TEXT),
                        Math.max(80, table.getColumnModel().getColumn(column).getWidth() - 12),
                        pixelScale()
                );
            } else {
                this.lines = List.of();
            }
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            int scale = pixelScale();
            if (column != 2) {
                return new Dimension(20, Math.max(16, MinecraftFont.lineHeight(scale) + 6));
            }
            return new Dimension(20, Math.max(16, lines.size() * MinecraftFont.lineHeight(scale) + 6));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            Color background = selected ? MinecraftTheme.SELECTION : switch (level) {
                case WARN -> new Color(0x332200);
                case ERROR -> new Color(0x330000);
                default -> MinecraftTheme.BG;
            };
            g2.setColor(background);
            g2.fillRect(0, 0, getWidth(), getHeight());

            int scale = pixelScale();
            int lineHeight = MinecraftFont.lineHeight(scale);
            int y = Math.max(3, (getHeight() - lineHeight) / 2);

            if (column == 0) {
                String text = String.valueOf(value);
                int textWidth = MinecraftFont.textWidth(text, scale);
                MinecraftFont.drawString(g2, text, Math.max(4, (getWidth() - textWidth) / 2), y, scale, MinecraftTheme.TEXT_MUTED, false);
            } else if (column == 1) {
                String text = String.valueOf(value);
                int textWidth = MinecraftFont.textWidth(text, scale);
                MinecraftFont.drawString(g2, text, Math.max(4, (getWidth() - textWidth) / 2), y, scale, switch (level) {
                    case WARN -> MinecraftTheme.WARN;
                    case ERROR -> MinecraftTheme.ERROR;
                    default -> MinecraftTheme.INFO;
                }, false);
            } else {
                y = 3;
                for (WrappedLine line : lines) {
                    int cursor = 6;
                    for (AnsiUtil.Segment segment : line.segments()) {
                        MinecraftFont.drawString(g2, segment.text(), cursor, y, scale, segment.color(), false);
                        cursor += MinecraftFont.textWidth(segment.text(), scale);
                    }
                    y += lineHeight;
                }
            }
            g2.dispose();
        }
    }

    private final class PlayerCellRenderer extends JComponent implements javax.swing.ListCellRenderer<String> {
        private String value = "";
        private boolean selected;

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
            this.value = value == null ? "" : value;
            this.selected = isSelected;
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(MinecraftTheme.scale(160), MinecraftTheme.scale(51));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setColor(selected ? MinecraftTheme.SELECTION : MinecraftTheme.BG);
            g2.fillRect(0, 0, getWidth(), getHeight());
            BufferedImage image = PlayerHeadCache.get(value, this::repaint);
            int head = MinecraftTheme.scale(36);
            int pad = MinecraftTheme.scale(8);
            MinecraftTheme.applyPixelRendering(g2);
            g2.drawImage(image, pad, pad, head, head, null);
            float fontSize = MinecraftUiFont.scaledSize(3);
            FontMetrics metrics = g2.getFontMetrics(MinecraftUiFont.font(fontSize));
            int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            MinecraftUiFont.draw(g2, value, pad + head + MinecraftTheme.scale(10), baseline, fontSize, MinecraftTheme.PANEL_TEXT, false);
            g2.dispose();
        }
    }

    private static List<WrappedLine> wrapSegments(List<AnsiUtil.Segment> segments, int maxWidth, int scale) {
        List<WrappedLine> lines = new ArrayList<>();
        List<AnsiUtil.Segment> current = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        Color currentColor = null;
        int width = 0;

        for (AnsiUtil.Segment segment : segments) {
            for (int i = 0; i < segment.text().length(); ) {
                int codePoint = segment.text().codePointAt(i);
                String unit = new String(Character.toChars(codePoint));
                if (codePoint == '\n') {
                    flushSegment(current, buffer, currentColor);
                    lines.add(new WrappedLine(current.isEmpty() ? List.of(new AnsiUtil.Segment("", MinecraftTheme.PANEL_TEXT)) : current));
                    current = new ArrayList<>();
                    buffer = new StringBuilder();
                    currentColor = null;
                    width = 0;
                    i += Character.charCount(codePoint);
                    continue;
                }

                int advance = MinecraftFont.textWidth(unit, scale);
                if (width + advance > maxWidth && width > 0) {
                    flushSegment(current, buffer, currentColor);
                    lines.add(new WrappedLine(current));
                    current = new ArrayList<>();
                    buffer = new StringBuilder();
                    currentColor = null;
                    width = 0;
                }

                if (currentColor == null || !currentColor.equals(segment.color())) {
                    flushSegment(current, buffer, currentColor);
                    currentColor = segment.color();
                }
                buffer.append(unit);
                width += advance;
                i += Character.charCount(codePoint);
            }
        }

        flushSegment(current, buffer, currentColor);
        if (!current.isEmpty()) lines.add(new WrappedLine(current));
        if (lines.isEmpty()) lines.add(new WrappedLine(List.of(new AnsiUtil.Segment("", MinecraftTheme.PANEL_TEXT))));
        return lines;
    }

    private static void flushSegment(List<AnsiUtil.Segment> current, StringBuilder buffer, Color currentColor) {
        if (currentColor != null && !buffer.isEmpty()) {
            current.add(new AnsiUtil.Segment(buffer.toString(), currentColor));
            buffer.setLength(0);
        }
    }

    private record WrappedLine(List<AnsiUtil.Segment> segments) {}

    private static final class ServerIconView extends JComponent {
        private BufferedImage icon;

        ServerIconView() {
            setOpaque(false);
        }

        void setIcon(BufferedImage icon) {
            this.icon = icon;
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            int size = MinecraftTheme.scale(44);
            return new Dimension(size, size);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            int size = Math.min(getWidth(), getHeight());
            if (size <= 0) return;
            Graphics2D g2 = (Graphics2D) graphics.create();
            MinecraftTheme.applyPixelRendering(g2);
            BufferedImage source = icon != null ? icon : MinecraftTheme.ICON;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;
            g2.drawImage(source, x, y, size, size, null);
            g2.dispose();
        }
    }

    private static final class MinecraftMenuItem extends JMenuItem {
        private final Color textColor;

        MinecraftMenuItem(String text, Color textColor) {
            super(text);
            this.textColor = textColor;
            setOpaque(false);
            setArmed(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        }

        @Override
        public Dimension getPreferredSize() {
            float fontSize = MinecraftUiFont.scaledSize(2);
            int width = MinecraftUiFont.textWidth(MinecraftUiFont.toSmallCaps(getText()), fontSize) + MinecraftTheme.scale(24);
            int height = Math.max(MinecraftTheme.scale(24), MinecraftUiFont.lineHeight(fontSize) + MinecraftTheme.scale(8));
            return new Dimension(width, height);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setColor(getModel().isArmed() || getModel().isRollover() ? MinecraftTheme.SELECTION : MinecraftTheme.BG);
            g2.fillRect(0, 0, getWidth(), getHeight());

            float fontSize = MinecraftUiFont.scaledSize(2);
            FontMetrics metrics = g2.getFontMetrics(MinecraftUiFont.font(fontSize));
            int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            MinecraftUiFont.draw(
                    g2,
                    MinecraftUiFont.toSmallCaps(getText()),
                    MinecraftTheme.scale(10),
                    baseline,
                    fontSize,
                    textColor,
                    false
            );
            g2.dispose();
        }
    }
}
