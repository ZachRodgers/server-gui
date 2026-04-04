package com.zach.minecraft.servergui.ui;

import com.zach.minecraft.servergui.config.AppConfig;
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
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.KeyboardFocusManager;
import java.awt.Taskbar;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JList;
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
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

public final class MainFrame extends JFrame {
    private final ServerController controller;
    private final LogTableModel logModel = new LogTableModel();
    private final DefaultListModel<String> playerModel = new DefaultListModel<>();

    private final Set<Level> visibleLevels = EnumSet.allOf(Level.class);
    private final Set<Category> visibleCategories = EnumSet.allOf(Category.class);

    private JTable logTable;
    private JScrollPane logScrollPane;
    private TableRowSorter<LogTableModel> logSorter;

    private final MinecraftTextField searchField = new MinecraftTextField("Search", 26, 2);
    private final MinecraftTextField commandField = new MinecraftTextField("Enter command", 32, 2);

    private final MinecraftLabel statusLabel = new MinecraftLabel("OFFLINE", 2);
    private final MinecraftButton startButton = new MinecraftButton("Start", 2);
    private final MinecraftButton stopButton = new MinecraftButton("Stop", 2);
    private final MinecraftButton restartButton = new MinecraftButton("Restart", 2);
    private final MinecraftButton killButton = new MinecraftButton("Force Kill", 2);

    private final ChartPanel tpsChart = new ChartPanel("TPS", "", MinecraftTheme.SUCCESS);
    private final ChartPanel heapChart = new ChartPanel("Heap", " MB", MinecraftTheme.ERROR);

    private int logFontSize = 16;

    public MainFrame(AppConfig config) {
        super(config.appTitle());
        this.controller = new ServerController(config);

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
        controller.start();
    }

    private JComponent buildUi(AppConfig config) {
        JPanel root = new JPanel(new BorderLayout(0, 10)) {
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D g2 = (Graphics2D) graphics.create();
                MinecraftTheme.paintTiledBackground(g2, MinecraftTheme.WINDOW_BG, 0, 0, getWidth(), getHeight(), 1f);
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        root.add(buildHeader(config), BorderLayout.NORTH);
        root.add(buildCenter(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        return root;
    }

    private JComponent buildHeader(AppConfig config) {
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));

        MinecraftLabel title = new MinecraftLabel(config.appTitle().toUpperCase(Locale.ROOT), 3);
        MinecraftLabel subtitle = new MinecraftLabel(
                (config.mockMode() ? "MOCK MODE" : config.workingDirectory().toString()).toUpperCase(Locale.ROOT),
                2
        );
        subtitle.setPixelColor(MinecraftTheme.TEXT_MUTED);

        titles.add(title);
        titles.add(Box.createVerticalStrut(4));
        titles.add(subtitle);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.setOpaque(false);
        controls.add(buildStatusPanel());
        controls.add(startButton);
        controls.add(stopButton);
        controls.add(restartButton);
        controls.add(killButton);

        top.add(titles, BorderLayout.WEST);
        top.add(controls, BorderLayout.EAST);
        return top;
    }

    private JComponent buildStatusPanel() {
        MinecraftPanel panel = new MinecraftPanel(true, 0.92f);
        panel.setBorder(new EmptyBorder(6, 10, 6, 10));
        panel.add(statusLabel, BorderLayout.CENTER);
        return panel;
    }

    private Component buildCenter() {
        buildLogTable();

        JPanel left = new JPanel(new BorderLayout(0, 8));
        left.setOpaque(false);
        left.add(buildLogToolbar(), BorderLayout.NORTH);
        left.add(logScrollPane, BorderLayout.CENTER);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));

        JComponent players = buildPlayersCard();
        players.setAlignmentX(Component.LEFT_ALIGNMENT);
        tpsChart.setAlignmentX(Component.LEFT_ALIGNMENT);
        heapChart.setAlignmentX(Component.LEFT_ALIGNMENT);

        right.add(players);
        right.add(Box.createVerticalStrut(8));
        right.add(tpsChart);
        right.add(Box.createVerticalStrut(8));
        right.add(heapChart);
        right.add(Box.createVerticalGlue());

        tpsChart.setPreferredSize(new Dimension(320, 160));
        heapChart.setPreferredSize(new Dimension(320, 160));
        tpsChart.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        heapChart.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

        JScrollPane rightScroll = new JScrollPane(right);
        styleScrollPane(rightScroll);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, rightScroll);
        split.setResizeWeight(0.73);
        split.setDividerSize(6);
        split.setOpaque(false);
        split.setBorder(null);
        return split;
    }

    private void buildLogTable() {
        logTable = new JTable(logModel);
        logTable.setOpaque(false);
        logTable.setFillsViewportHeight(true);
        logTable.setShowHorizontalLines(false);
        logTable.setShowVerticalLines(false);
        logTable.setIntercellSpacing(new Dimension(0, 0));
        logTable.setRowHeight(22);
        logTable.setTableHeader(null);
        logTable.setDefaultRenderer(Object.class, new PixelLogCellRenderer());
        logTable.setSelectionBackground(MinecraftTheme.SELECTION);
        logTable.setSelectionForeground(MinecraftTheme.PANEL_TEXT);
        logTable.setRowMargin(0);

        TableColumnModel cols = logTable.getColumnModel();
        cols.getColumn(0).setMinWidth(104);
        cols.getColumn(0).setMaxWidth(110);
        cols.getColumn(1).setMinWidth(86);
        cols.getColumn(1).setMaxWidth(94);

        logSorter = new TableRowSorter<>(logModel);
        for (int i = 0; i < logModel.getColumnCount(); i++) logSorter.setSortable(i, false);
        logTable.setRowSorter(logSorter);
        searchField.getDocument().addDocumentListener(SimpleDocumentListener.onChange(this::applyFilters));

        logScrollPane = new JScrollPane(logTable);
        styleScrollPane(logScrollPane);
        logScrollPane.getViewport().setOpaque(false);
        logScrollPane.setViewportView(logTable);
    }

    private void styleScrollPane(JScrollPane scrollPane) {
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createLineBorder(MinecraftTheme.TEXT_DARK));
        scrollPane.getVerticalScrollBar().setUI(new MinecraftScrollBarUI());
        scrollPane.getHorizontalScrollBar().setUI(new MinecraftScrollBarUI());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
    }

    private JComponent buildLogToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setOpaque(false);
        bar.add(searchField);
        bar.add(buildFilterButton());
        return bar;
    }

    private MinecraftButton buildFilterButton() {
        MinecraftButton button = new MinecraftButton("Filter", 2);

        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createLineBorder(MinecraftTheme.TEXT_DARK));
        popup.setBackground(MinecraftTheme.BG);

        popup.add(checkItem("INFO", Level.INFO, null, MinecraftTheme.INFO));
        popup.add(checkItem("WARN", Level.WARN, null, MinecraftTheme.WARN));
        popup.add(checkItem("ERROR", Level.ERROR, null, MinecraftTheme.ERROR));
        popup.add(new JSeparator());
        popup.add(checkItem("CHAT", null, Category.CHAT, MinecraftTheme.CHAT));
        popup.add(checkItem("COMMANDS", null, Category.COMMAND, MinecraftTheme.COMMAND));
        popup.add(checkItem("SYSTEM", null, Category.SYSTEM, MinecraftTheme.TEXT_MUTED));

        button.addActionListener(e -> popup.show(button, 0, button.getHeight()));
        return button;
    }

    private JCheckBoxMenuItem checkItem(String text, Level level, Category category, Color color) {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(text, true);
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
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

        MinecraftLabel title = new MinecraftLabel("PLAYERS ONLINE", 2);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        inner.add(title);
        inner.add(Box.createVerticalStrut(6));

        JList<String> list = new JList<>(playerModel);
        list.setOpaque(false);
        list.setCellRenderer(new PlayerCellRenderer());

        JScrollPane scroller = new JScrollPane(list);
        styleScrollPane(scroller);
        scroller.setPreferredSize(new Dimension(300, 180));
        scroller.setAlignmentX(Component.LEFT_ALIGNMENT);
        inner.add(scroller);

        panel.add(inner, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setOpaque(false);
        footer.add(commandField, BorderLayout.CENTER);
        MinecraftButton send = new MinecraftButton("Send", 2);
        send.addActionListener(e -> sendCommand());
        footer.add(send, BorderLayout.EAST);
        commandField.addActionListener(e -> sendCommand());
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
                    adjustFontSize(+2);
                    return true;
                }
                if (key == KeyEvent.VK_MINUS || key == KeyEvent.VK_SUBTRACT) {
                    adjustFontSize(-2);
                    return true;
                }
            }
            return false;
        });
    }

    private void sendCommand() {
        String command = commandField.getText().trim();
        if (command.isEmpty()) return;
        controller.sendCommand(command);
        commandField.setText("");
    }

    private void updatePlayers(List<String> players) {
        playerModel.clear();
        players.forEach(playerModel::addElement);
    }

    private void updateHeap(HeapSample sample) {
        heapChart.addPoint(sample.usedMb(), sample.totalMb());
    }

    private void updateStatus(ServerStatus status) {
        switch (status) {
            case LOADING -> {
                statusLabel.setPixelText("LOADING");
                statusLabel.setPixelColor(MinecraftTheme.WARN);
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                restartButton.setEnabled(false);
                killButton.setEnabled(true);
            }
            case ONLINE -> {
                statusLabel.setPixelText("ONLINE");
                statusLabel.setPixelColor(MinecraftTheme.SUCCESS);
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                restartButton.setEnabled(true);
                killButton.setEnabled(true);
            }
            case OFFLINE -> {
                statusLabel.setPixelText("OFFLINE");
                statusLabel.setPixelColor(MinecraftTheme.ERROR);
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                restartButton.setEnabled(false);
                killButton.setEnabled(false);
            }
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
        scrollToBottom();
    }

    private void maybeScrollToBottom() {
        if (logScrollPane == null) return;
        JScrollBar bar = logScrollPane.getVerticalScrollBar();
        if (bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 40) {
            scrollToBottom();
        }
    }

    private void scrollToBottom() {
        if (logTable == null) return;
        SwingUtilities.invokeLater(() -> {
            int last = logTable.getRowCount() - 1;
            if (last >= 0) logTable.scrollRectToVisible(logTable.getCellRect(last, 0, true));
        });
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

    private void adjustFontSize(int delta) {
        logFontSize = Math.max(8, Math.min(24, logFontSize + delta));
        logTable.setRowHeight(pixelScale() == 1 ? 14 : pixelScale() == 2 ? 22 : 30);
        logTable.repaint();
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

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            int modelRow = table.convertRowIndexToModel(row);
            this.value = value;
            this.level = (Level) table.getModel().getValueAt(modelRow, 1);
            this.selected = isSelected;
            this.column = column;
            return this;
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
            int textY = (getHeight() - MinecraftFont.lineHeight(scale)) / 2;

            if (column == 0) {
                String text = String.valueOf(value);
                int textWidth = MinecraftFont.textWidth(text, scale);
                MinecraftFont.drawString(g2, text, Math.max(4, (getWidth() - textWidth) / 2), textY, scale, MinecraftTheme.TEXT_MUTED, false);
            } else if (column == 1) {
                String text = String.valueOf(value);
                int textWidth = MinecraftFont.textWidth(text, scale);
                MinecraftFont.drawString(g2, text, Math.max(4, (getWidth() - textWidth) / 2), textY, scale, switch (level) {
                    case WARN -> MinecraftTheme.WARN;
                    case ERROR -> MinecraftTheme.ERROR;
                    default -> MinecraftTheme.INFO;
                }, false);
            } else {
                int cursor = 6;
                String raw = String.valueOf(value);
                for (AnsiUtil.Segment segment : AnsiUtil.segments(raw, MinecraftTheme.PANEL_TEXT)) {
                    MinecraftFont.drawString(g2, segment.text(), cursor, textY, scale, segment.color(), false);
                    cursor += MinecraftFont.textWidth(segment.text(), scale);
                }
            }
            g2.dispose();
        }
    }

    private static final class PlayerCellRenderer extends JComponent implements javax.swing.ListCellRenderer<String> {
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
            return new Dimension(100, 22);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setColor(selected ? MinecraftTheme.SELECTION : MinecraftTheme.BG);
            g2.fillRect(0, 0, getWidth(), getHeight());
            MinecraftFont.drawString(g2, value, 6, 3, 2, MinecraftTheme.PANEL_TEXT, false);
            g2.dispose();
        }
    }
}
