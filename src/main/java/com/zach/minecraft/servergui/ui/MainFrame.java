package com.zach.minecraft.servergui.ui;

import com.zach.minecraft.servergui.config.AppConfig;
import com.zach.minecraft.servergui.model.HeapSample;
import com.zach.minecraft.servergui.model.LogEntry;
import com.zach.minecraft.servergui.model.LogEntry.Category;
import com.zach.minecraft.servergui.model.LogEntry.Level;
import com.zach.minecraft.servergui.service.ServerController;
import com.zach.minecraft.servergui.service.ServerController.ServerStatus;
import com.zach.minecraft.servergui.util.AnsiUtil;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
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
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

public final class MainFrame extends JFrame {

    // ── shadcn/ui dark palette ─────────────────────────────────────────────
    // zinc-950 / zinc-900 / zinc-800 / zinc-50 / zinc-500
    static final Color BG          = new Color(0x09090B);  // page background
    static final Color CARD        = new Color(0x18181B);  // card / raised surface
    static final Color BORDER      = new Color(0x27272A);  // all borders
    static final Color MUTED       = new Color(0x27272A);  // muted fill (selection)
    static final Color TEXT        = new Color(0xFAFAFA);  // primary text
    static final Color TEXT_MUTED  = new Color(0x71717A);  // secondary / placeholder
    static final Color TEXT_SEC    = new Color(0xA1A1AA);  // medium text

    // Semantic foregrounds (text only — used for badges and level labels)
    static final Color FG_GREEN    = new Color(0x4ADE80);  // green-400
    static final Color FG_AMBER    = new Color(0xFBBF24);  // amber-400
    static final Color FG_RED      = new Color(0xF87171);  // red-400
    static final Color FG_ROSE     = new Color(0xFCA5A5);  // rose-300

    // Semantic button fills (very dark tint + colored border)
    static final Color BTN_GREEN_BG     = new Color(0x052E16);  // green-950
    static final Color BTN_GREEN_BD     = new Color(0x166534);  // green-800
    static final Color BTN_RED_BG       = new Color(0x1C0202);  // red-950
    static final Color BTN_RED_BD       = new Color(0x7F1D1D);  // red-900
    static final Color BTN_AMBER_BG     = new Color(0x1C1002);  // amber-950
    static final Color BTN_AMBER_BD     = new Color(0x92400E);  // amber-800
    static final Color BTN_ROSE_BG      = new Color(0x1C0505);  // rose-950
    static final Color BTN_ROSE_BD      = new Color(0x881337);  // rose-900

    // Log row tints (barely-there)
    static final Color WARN_ROW    = new Color(0x15100A);
    static final Color ERROR_ROW   = new Color(0x150A0A);

    // Level text
    static final Color WARN_FG     = new Color(0xF59E0B);  // amber-500
    static final Color ERROR_FG    = new Color(0xEF4444);  // red-500
    static final Color INFO_FG     = new Color(0x52525B);  // zinc-600

    // ── State ──────────────────────────────────────────────────────────────
    private final ServerController controller;
    private final LogTableModel logModel = new LogTableModel();
    private final DefaultListModel<String> playerModel = new DefaultListModel<>();

    private final Set<Level>    visibleLevels     = EnumSet.allOf(Level.class);
    private final Set<Category> visibleCategories = EnumSet.allOf(Category.class);

    private JTable     logTable;
    private JScrollPane logScrollPane;
    private TableRowSorter<LogTableModel> logSorter;

    private final JTextField searchField  = new JTextField();
    private final JTextField commandField = new JTextField();

    private final StatusDot statusDot   = new StatusDot();
    private final JLabel    statusLabel = new JLabel("OFFLINE");

    // Buttons: semantic outline style (tinted bg + colored border + colored text)
    private final JButton startButton   = outlineBtn("Start",      FG_GREEN, BTN_GREEN_BG, BTN_GREEN_BD);
    private final JButton stopButton    = outlineBtn("Stop",       FG_RED,   BTN_RED_BG,   BTN_RED_BD);
    private final JButton restartButton = outlineBtn("Restart",    FG_AMBER, BTN_AMBER_BG, BTN_AMBER_BD);
    private final JButton killButton    = outlineBtn("Force Kill", FG_ROSE,  BTN_ROSE_BG,  BTN_ROSE_BD);

    private final ChartPanel tpsChart  = new ChartPanel("TPS",  "",    FG_GREEN);
    private final ChartPanel heapChart = new ChartPanel("Heap", " MB", FG_RED);

    private int logFontSize = 12;

    // ── Constructor ────────────────────────────────────────────────────────

    public MainFrame(AppConfig config) {
        super(config.appTitle());
        this.controller = new ServerController(config);

        patchDefaults();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1200, 760));
        setLocationByPlatform(true);
        installWindowIcons();
        setContentPane(buildUi(config));
        attachEvents();
        installZoomKeys();
    }

    public void autoStart() { controller.start(); }

    // ── UI construction ────────────────────────────────────────────────────

    private JPanel buildUi(AppConfig config) {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        root.add(buildHeader(config), BorderLayout.NORTH);
        root.add(buildCenter(),       BorderLayout.CENTER);
        root.add(buildFooter(),       BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildHeader(AppConfig config) {
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(config.appTitle());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setForeground(TEXT);
        JLabel sub = new JLabel(config.mockMode()
                ? "Mock mode — edit server-wrapper.properties to use a real server"
                : config.workingDirectory().toString());
        sub.setFont(sub.getFont().deriveFont(11f));
        sub.setForeground(TEXT_MUTED);
        titles.add(title);
        titles.add(Box.createVerticalStrut(2));
        titles.add(sub);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        controls.setOpaque(false);
        controls.add(buildStatusBadge());
        controls.add(Box.createHorizontalStrut(4));
        controls.add(startButton);
        controls.add(stopButton);
        controls.add(restartButton);
        controls.add(killButton);

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(titles,   BorderLayout.WEST);
        row.add(controls, BorderLayout.EAST);

        JPanel wrapper = new JPanel(new BorderLayout(0, 12));
        wrapper.setOpaque(false);
        wrapper.add(row,    BorderLayout.CENTER);
        wrapper.add(hRule(), BorderLayout.SOUTH);
        return wrapper;
    }

    private JPanel buildStatusBadge() {
        JPanel badge = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.setColor(BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
            }
        };
        badge.setOpaque(false);
        badge.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        statusDot.setPreferredSize(new Dimension(8, 8));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));
        statusLabel.setForeground(FG_RED);
        badge.add(statusDot);
        badge.add(statusLabel);
        return badge;
    }

    private Component buildCenter() {
        buildLogTable();

        JPanel left = new JPanel(new BorderLayout(0, 8));
        left.setOpaque(false);
        left.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        left.add(buildLogToolbar(), BorderLayout.NORTH);
        left.add(logScrollPane,    BorderLayout.CENTER);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 0));

        JPanel playersCard = buildPlayersCard();
        playersCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        tpsChart.setAlignmentX(Component.LEFT_ALIGNMENT);
        heapChart.setAlignmentX(Component.LEFT_ALIGNMENT);

        right.add(playersCard);
        right.add(Box.createVerticalStrut(8));
        right.add(tpsChart);
        right.add(Box.createVerticalStrut(8));
        right.add(heapChart);
        right.add(Box.createVerticalGlue());

        tpsChart.setPreferredSize(new Dimension(300, 148));
        tpsChart.setMinimumSize(new Dimension(200, 120));
        tpsChart.setMaximumSize(new Dimension(Short.MAX_VALUE, 148));
        heapChart.setPreferredSize(new Dimension(300, 148));
        heapChart.setMinimumSize(new Dimension(200, 120));
        heapChart.setMaximumSize(new Dimension(Short.MAX_VALUE, 148));

        JScrollPane rightScroll = new JScrollPane(right,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        rightScroll.setBorder(null);
        rightScroll.setBackground(BG);
        rightScroll.getViewport().setBackground(BG);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, rightScroll);
        split.setResizeWeight(0.73);
        split.setBorder(null);
        split.setBackground(BG);
        split.setDividerSize(5);
        return split;
    }

    private void buildLogTable() {
        logTable = new JTable(logModel);
        logTable.setBackground(BG);
        logTable.setForeground(TEXT);
        logTable.setGridColor(BORDER);
        logTable.setShowHorizontalLines(true);
        logTable.setShowVerticalLines(false);
        logTable.setIntercellSpacing(new Dimension(0, 0));
        logTable.setRowHeight(26);
        logTable.setSelectionBackground(MUTED);
        logTable.setSelectionForeground(TEXT);
        logTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, logFontSize));
        logTable.getTableHeader().setBackground(CARD);
        logTable.getTableHeader().setForeground(TEXT_MUTED);
        logTable.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        logTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        TableColumnModel cols = logTable.getColumnModel();
        cols.getColumn(0).setMinWidth(68); cols.getColumn(0).setMaxWidth(76); cols.getColumn(0).setPreferredWidth(72);
        cols.getColumn(1).setMinWidth(50); cols.getColumn(1).setMaxWidth(58); cols.getColumn(1).setPreferredWidth(54);

        logTable.setDefaultRenderer(Object.class, new LogCellRenderer());

        logSorter = new TableRowSorter<>(logModel);
        for (int i = 0; i < logModel.getColumnCount(); i++) logSorter.setSortable(i, false);
        logTable.setRowSorter(logSorter);

        searchField.getDocument().addDocumentListener(
                SimpleDocumentListener.onChange(this::applyFilters));

        logScrollPane = new JScrollPane(logTable) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        logScrollPane.setBackground(BG);
        logScrollPane.getViewport().setBackground(BG);
        logScrollPane.setBorder(BorderFactory.createLineBorder(BORDER, 1, true));
    }

    private JPanel buildLogToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bar.setOpaque(false);

        searchField.setColumns(26);
        searchField.setBackground(CARD);
        searchField.setForeground(TEXT);
        searchField.setCaretColor(TEXT);
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        searchField.putClientProperty("JTextField.placeholderText", "Search…");

        bar.add(searchField);
        bar.add(buildFilterButton());
        return bar;
    }

    private JButton buildFilterButton() {
        JButton btn = ghostBtn("Filter ▾");

        JPopupMenu popup = new JPopupMenu();
        popup.setBackground(CARD);
        popup.setBorder(BorderFactory.createLineBorder(BORDER));

        popup.add(popupHeader("Level"));
        for (Level lv : Level.values()) {
            JCheckBoxMenuItem item = checkItem(lv.name(), levelFg(lv));
            item.addActionListener(e -> {
                if (item.isSelected()) visibleLevels.add(lv); else visibleLevels.remove(lv);
                applyFilters();
            });
            popup.add(item);
        }
        popup.add(new JSeparator());
        popup.add(popupHeader("Category"));

        JCheckBoxMenuItem chatItem = checkItem("Chat",     new Color(0x60A5FA));
        chatItem.addActionListener(e -> { toggle(visibleCategories, Category.CHAT, chatItem.isSelected()); applyFilters(); });
        JCheckBoxMenuItem cmdItem  = checkItem("Commands", new Color(0xA78BFA));
        cmdItem.addActionListener(e ->  { toggle(visibleCategories, Category.COMMAND, cmdItem.isSelected()); applyFilters(); });
        JCheckBoxMenuItem sysItem  = checkItem("System",   TEXT_SEC);
        sysItem.addActionListener(e ->  { toggle(visibleCategories, Category.SYSTEM, sysItem.isSelected()); applyFilters(); });
        popup.add(chatItem); popup.add(cmdItem); popup.add(sysItem);

        btn.addActionListener(e -> popup.show(btn, 0, btn.getHeight()));
        return btn;
    }

    private static <T> void toggle(Set<T> set, T value, boolean add) {
        if (add) set.add(value); else set.remove(value);
    }

    private JPanel buildPlayersCard() {
        JPanel card = new JPanel(new BorderLayout(0, 8)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.setColor(BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setMaximumSize(new Dimension(Short.MAX_VALUE, 190));
        card.setPreferredSize(new Dimension(300, 190));
        card.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel title = new JLabel("Players Online");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
        title.setForeground(TEXT_SEC);

        JList<String> list = new JList<>(playerModel);
        list.setBackground(CARD);
        list.setForeground(TEXT);
        list.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        list.setBorder(null);

        JScrollPane sp = new JScrollPane(list);
        sp.setBackground(CARD);
        sp.getViewport().setBackground(CARD);
        sp.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));

        card.add(title, BorderLayout.NORTH);
        card.add(sp,    BorderLayout.CENTER);
        return card;
    }

    private JPanel buildFooter() {
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        commandField.setBackground(CARD);
        commandField.setForeground(TEXT);
        commandField.setCaretColor(TEXT);
        commandField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        commandField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        commandField.putClientProperty("JTextField.placeholderText", "Enter command…");
        commandField.addActionListener(e -> sendCommand());

        JButton sendBtn = ghostBtn("Send");
        sendBtn.addActionListener(e -> sendCommand());

        panel.add(commandField, BorderLayout.CENTER);
        panel.add(sendBtn,      BorderLayout.EAST);
        return panel;
    }

    // ── Event wiring ───────────────────────────────────────────────────────

    private void attachEvents() {
        startButton.addActionListener(e -> controller.start());
        stopButton.addActionListener(e -> controller.stop());
        restartButton.addActionListener(e -> controller.restart());
        killButton.addActionListener(e -> controller.forceKill());

        controller.onLog(entry -> SwingUtilities.invokeLater(() -> {
            logModel.add(entry);
            maybeScrollToBottom();
        }));
        controller.onPlayers(p -> SwingUtilities.invokeLater(() -> updatePlayers(p)));
        controller.onTps(v -> SwingUtilities.invokeLater(() -> tpsChart.addPoint(v, 20.0)));
        controller.onHeap(s -> SwingUtilities.invokeLater(() -> updateHeap(s)));
        controller.onStatus(s -> SwingUtilities.invokeLater(() -> updateStatus(s)));

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { controller.shutdown(); dispose(); }
        });
    }

    private void installZoomKeys() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED && e.isControlDown()) {
                int k = e.getKeyCode();
                // VK_EQUALS = '=' key (same key as '+' on QWERTY), VK_ADD = numpad '+'
                if (k == KeyEvent.VK_EQUALS || k == KeyEvent.VK_ADD)      { adjustFontSize(+1); return true; }
                if (k == KeyEvent.VK_MINUS  || k == KeyEvent.VK_SUBTRACT) { adjustFontSize(-1); return true; }
            }
            return false;
        });
    }

    // ── Logic ──────────────────────────────────────────────────────────────

    private void sendCommand() {
        String cmd = commandField.getText().trim();
        if (cmd.isEmpty()) return;
        controller.sendCommand(cmd);
        commandField.setText("");
    }

    private void updatePlayers(List<String> players) {
        playerModel.clear();
        players.forEach(playerModel::addElement);
    }

    private void updateHeap(HeapSample s) { heapChart.addPoint(s.usedMb(), s.totalMb()); }

    private void updateStatus(ServerStatus status) {
        switch (status) {
            case LOADING -> {
                statusLabel.setText("Loading");  statusLabel.setForeground(FG_AMBER);
                statusDot.setColor(FG_AMBER);
                startButton.setEnabled(false); stopButton.setEnabled(true);
                restartButton.setEnabled(false); killButton.setEnabled(true);
            }
            case ONLINE -> {
                statusLabel.setText("Online");   statusLabel.setForeground(FG_GREEN);
                statusDot.setColor(FG_GREEN);
                startButton.setEnabled(false); stopButton.setEnabled(true);
                restartButton.setEnabled(true);  killButton.setEnabled(true);
            }
            case OFFLINE -> {
                statusLabel.setText("Offline");  statusLabel.setForeground(FG_RED);
                statusDot.setColor(FG_RED);
                startButton.setEnabled(true);  stopButton.setEnabled(false);
                restartButton.setEnabled(false); killButton.setEnabled(false);
            }
        }
    }

    private void applyFilters() {
        String search = searchField.getText().trim().toLowerCase(Locale.ROOT);
        logSorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends LogTableModel, ? extends Integer> entry) {
                int modelRow = (Integer) entry.getIdentifier();
                LogEntry e = logModel.getEntry(modelRow);
                if (!visibleLevels.contains(e.level()))        return false;
                if (!visibleCategories.contains(e.category())) return false;
                if (!search.isBlank()) {
                    String msg = AnsiUtil.strip(String.valueOf(entry.getValue(2))).toLowerCase(Locale.ROOT);
                    return msg.contains(search);
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
            int last = logTable.getRowCount() - 1;
            if (last >= 0) logTable.scrollRectToVisible(logTable.getCellRect(last, 0, true));
        }
    }

    private void scrollToBottom() {
        if (logScrollPane == null || logTable == null) return;
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
            } catch (UnsupportedOperationException | SecurityException ignored) {}
        }
    }

    private void adjustFontSize(int delta) {
        logFontSize = Math.max(9, Math.min(24, logFontSize + delta));
        logTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, logFontSize));
        logTable.setRowHeight(Math.max(20, logFontSize + 10));
        logTable.repaint();
    }

    // ── Static factories ───────────────────────────────────────────────────

    /** Tinted-background outline button — shadcn semantic button pattern. */
    private static JButton outlineBtn(String text, Color fg, Color bg, Color border) {
        JButton btn = new JButton(text);
        btn.setForeground(fg);
        btn.setBackground(bg);
        btn.setFocusPainted(false);
        btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 12f));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border),
                BorderFactory.createEmptyBorder(5, 12, 5, 12)));
        btn.setOpaque(true);
        return btn;
    }

    /** Neutral ghost button for secondary actions (Filter, Send). */
    private static JButton ghostBtn(String text) {
        JButton btn = new JButton(text);
        btn.setForeground(TEXT_SEC);
        btn.setBackground(CARD);
        btn.setFocusPainted(false);
        btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 12f));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(5, 12, 5, 12)));
        btn.setOpaque(true);
        return btn;
    }

    private static JCheckBoxMenuItem checkItem(String text, Color fg) {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(text, true);
        item.setBackground(CARD);
        item.setForeground(fg);
        item.setFont(item.getFont().deriveFont(Font.PLAIN, 12f));
        return item;
    }

    private static JLabel popupHeader(String text) {
        JLabel lbl = new JLabel("  " + text.toUpperCase(Locale.ROOT));
        lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN, 10f));
        lbl.setForeground(TEXT_MUTED);
        lbl.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
        return lbl;
    }

    private static Color levelFg(Level level) {
        return switch (level) { case WARN -> WARN_FG; case ERROR -> ERROR_FG; default -> INFO_FG; };
    }

    private static JPanel hRule() {
        JPanel rule = new JPanel();
        rule.setOpaque(false);
        rule.setPreferredSize(new Dimension(0, 1));
        rule.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        return rule;
    }

    private static void patchDefaults() {
        // Rounded corners (FlatLaf honours these)
        UIManager.put("Button.arc",          8);
        UIManager.put("Component.arc",       8);
        UIManager.put("TextComponent.arc",   8);
        // Core backgrounds
        UIManager.put("Table.background",              BG);
        UIManager.put("Table.foreground",              TEXT);
        UIManager.put("Table.selectionBackground",     MUTED);
        UIManager.put("Table.selectionForeground",     TEXT);
        UIManager.put("Table.gridColor",               BORDER);
        UIManager.put("ScrollPane.background",         BG);
        UIManager.put("Viewport.background",           BG);
        UIManager.put("List.background",               CARD);
        UIManager.put("List.foreground",               TEXT);
        UIManager.put("List.selectionBackground",      MUTED);
        UIManager.put("List.selectionForeground",      TEXT);
        // Popup / menu
        UIManager.put("PopupMenu.background",          CARD);
        UIManager.put("PopupMenu.border",              BorderFactory.createLineBorder(BORDER));
        UIManager.put("MenuItem.background",           CARD);
        UIManager.put("MenuItem.foreground",           TEXT);
        UIManager.put("MenuItem.selectionBackground",  MUTED);
        UIManager.put("CheckBoxMenuItem.background",   CARD);
        UIManager.put("CheckBoxMenuItem.foreground",   TEXT);
        UIManager.put("Separator.foreground",          BORDER);
        // Split pane
        UIManager.put("SplitPane.background",          BG);
        UIManager.put("SplitPaneDivider.draggingColor",BORDER);
        // Scrollbar
        UIManager.put("ScrollBar.background",          BG);
        UIManager.put("ScrollBar.thumb",               new Color(0x3F3F46));
        UIManager.put("ScrollBar.track",               BG);
    }

    // ── Inner classes ──────────────────────────────────────────────────────

    private static final class StatusDot extends JPanel {
        private Color color = FG_RED;
        StatusDot() { setOpaque(false); }
        void setColor(Color c) { this.color = c; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int s = Math.min(getWidth(), getHeight());
            int x = (getWidth() - s) / 2, y = (getHeight() - s) / 2;
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
            g2.fillOval(x - 2, y - 2, s + 4, s + 4);
            g2.setColor(color);
            g2.fillOval(x, y, s, s);
            g2.dispose();
        }
    }

    private final class LogCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {

            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            int modelRow = table.convertRowIndexToModel(row);
            Level level = (Level) table.getModel().getValueAt(modelRow, 1);

            if (!isSelected) {
                setBackground(switch (level) {
                    case WARN  -> WARN_ROW;
                    case ERROR -> ERROR_ROW;
                    default    -> BG;
                });
            }

            setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));

            switch (column) {
                case 0 -> {
                    setHorizontalAlignment(CENTER);
                    setForeground(isSelected ? TEXT : TEXT_MUTED);
                    setFont(table.getFont());
                }
                case 1 -> {
                    setHorizontalAlignment(CENTER);
                    setForeground(isSelected ? TEXT : levelFg(level));
                    setFont(table.getFont().deriveFont(Font.PLAIN, table.getFont().getSize() - 1f));
                }
                case 2 -> {
                    setHorizontalAlignment(LEFT);
                    setFont(table.getFont());
                    String raw = String.valueOf(value);
                    if (raw.contains("\u001B[") || raw.contains("[3") || raw.contains("[9") || raw.contains("[0m")) {
                        setText(AnsiUtil.toHtml(raw));
                    } else {
                        setForeground(isSelected ? TEXT : TEXT);  // full brightness on dark bg
                    }
                }
            }
            return this;
        }
    }
}
