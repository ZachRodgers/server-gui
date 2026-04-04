package com.zach.minecraft.servergui.ui;

import com.zach.minecraft.servergui.config.AppConfig;
import com.zach.minecraft.servergui.model.HeapSample;
import com.zach.minecraft.servergui.model.LogEntry;
import com.zach.minecraft.servergui.model.LogEntry.Level;
import com.zach.minecraft.servergui.service.ServerController;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

public final class MainFrame extends JFrame {
    private final ServerController controller;
    private final LogTableModel logTableModel = new LogTableModel();
    private final DefaultListModel<String> playerModel = new DefaultListModel<>();
    private final JLabel statusValue = new JLabel("Stopped");
    private final JButton startButton = new JButton("Start");
    private final JTextField commandField = new JTextField();
    private final JComboBox<String> severityFilter = new JComboBox<>(new String[]{"All", "INFO", "WARN", "ERROR"});
    private final JTextField searchField = new JTextField();
    private final ChartPanel tpsChart = new ChartPanel("TPS", "", new Color(0x1F8A70));
    private final ChartPanel heapChart = new ChartPanel("Heap", " MB", new Color(0xC96C50));

    public MainFrame(AppConfig config) {
        super(config.appTitle());
        this.controller = new ServerController(config);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 760));
        setLocationByPlatform(true);
        setContentPane(buildUi(config));
        attachEvents();
    }

    private JPanel buildUi(AppConfig config) {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        root.setBackground(new Color(0xEEF3F8));

        root.add(buildHeader(config), BorderLayout.NORTH);
        root.add(buildMainContent(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildHeader(AppConfig config) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        JPanel titlePanel = new JPanel();
        titlePanel.setOpaque(false);
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel(config.appTitle());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        JLabel subtitle = new JLabel(config.mockMode()
                ? "Mock mode active. Edit server-wrapper.properties to point at a real server."
                : "Launching from " + config.workingDirectory());
        subtitle.setForeground(new Color(0x607080));

        titlePanel.add(title);
        titlePanel.add(Box.createVerticalStrut(4));
        titlePanel.add(subtitle);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        startButton.setFocusPainted(false);
        JButton stopButton = new JButton("Stop");
        stopButton.setFocusPainted(false);
        stopButton.addActionListener(event -> controller.stop());

        JLabel statusLabel = new JLabel("Status");
        statusLabel.setForeground(new Color(0x607080));
        statusValue.setForeground(new Color(0x9F2D2D));

        actions.add(statusLabel);
        actions.add(statusValue);
        actions.add(startButton);
        actions.add(stopButton);

        panel.add(titlePanel, BorderLayout.WEST);
        panel.add(actions, BorderLayout.EAST);
        return panel;
    }

    private Component buildMainContent() {
        JTable table = new JTable(logTableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(28);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(new Color(0xDDEBFB));
        table.setDefaultRenderer(Object.class, new LogCellRenderer());

        TableRowSorter<LogTableModel> sorter = new TableRowSorter<>(logTableModel);
        table.setRowSorter(sorter);
        severityFilter.addActionListener(event -> applyFilters(sorter));
        searchField.getDocument().addDocumentListener(SimpleDocumentListener.onChange(() -> applyFilters(sorter)));

        JPanel left = new JPanel(new BorderLayout(8, 8));
        left.setOpaque(false);
        left.add(buildLogFilters(), BorderLayout.NORTH);
        left.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(buildPlayersCard());
        right.add(Box.createVerticalStrut(12));
        right.add(tpsChart);
        right.add(Box.createVerticalStrut(12));
        right.add(heapChart);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        splitPane.setResizeWeight(0.72);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);

        tpsChart.setPreferredSize(new Dimension(320, 180));
        heapChart.setPreferredSize(new Dimension(320, 180));

        return splitPane;
    }

    private JPanel buildLogFilters() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        panel.setOpaque(false);

        JLabel severityLabel = new JLabel("Severity");
        severityLabel.setForeground(new Color(0x607080));
        JLabel searchLabel = new JLabel("Search");
        searchLabel.setForeground(new Color(0x607080));

        searchField.setColumns(20);
        panel.add(severityLabel);
        panel.add(severityFilter);
        panel.add(searchLabel);
        panel.add(searchField);
        return panel;
    }

    private JPanel buildPlayersCard() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(true);
        panel.setBackground(new Color(0xF6F8FB));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xD9E0EA)),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)
        ));

        JLabel title = new JLabel("Players");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        JList<String> playerList = new JList<>(playerModel);
        playerList.setVisibleRowCount(8);
        playerList.setBackground(new Color(0xF6F8FB));
        playerList.setBorder(null);

        panel.add(title, BorderLayout.NORTH);
        panel.add(new JScrollPane(playerList), BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(320, 220));
        return panel;
    }

    private JPanel buildFooter() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);

        commandField.addActionListener(event -> sendCommand());
        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(event -> sendCommand());

        panel.add(commandField, BorderLayout.CENTER);
        panel.add(sendButton, BorderLayout.EAST);
        return panel;
    }

    private void attachEvents() {
        startButton.addActionListener(event -> controller.start());

        controller.onLog(entry -> SwingUtilities.invokeLater(() -> {
            logTableModel.add(entry);
        }));
        controller.onPlayers(players -> SwingUtilities.invokeLater(() -> updatePlayers(players)));
        controller.onTps(value -> SwingUtilities.invokeLater(() -> tpsChart.addPoint(value, 20.0)));
        controller.onHeap(sample -> SwingUtilities.invokeLater(() -> updateHeap(sample)));
        controller.onRunning(running -> SwingUtilities.invokeLater(() -> updateRunningState(running)));

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                controller.shutdown();
                dispose();
            }
        });
    }

    private void sendCommand() {
        String command = commandField.getText().trim();
        if (command.isEmpty()) {
            return;
        }
        controller.sendCommand(command);
        commandField.setText("");
    }

    private void updatePlayers(List<String> players) {
        playerModel.clear();
        for (String player : players) {
            playerModel.addElement(player);
        }
    }

    private void updateHeap(HeapSample sample) {
        heapChart.addPoint(sample.usedMb(), sample.totalMb());
    }

    private void updateRunningState(boolean running) {
        statusValue.setText(running ? "Running" : "Stopped");
        statusValue.setForeground(running ? new Color(0x1F8A70) : new Color(0x9F2D2D));
        startButton.setEnabled(!running);
    }

    private void applyFilters(TableRowSorter<LogTableModel> sorter) {
        String severity = String.valueOf(severityFilter.getSelectedItem());
        String search = searchField.getText().trim().toLowerCase(Locale.ROOT);

        sorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends LogTableModel, ? extends Integer> entry) {
                String rowSeverity = String.valueOf(entry.getValue(1));
                String message = String.valueOf(entry.getValue(2)).toLowerCase(Locale.ROOT);

                boolean severityMatches = "All".equals(severity) || severity.equals(rowSeverity);
                boolean searchMatches = search.isBlank() || message.contains(search);
                return severityMatches && searchMatches;
            }
        });
    }

    private static final class LogCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column
        ) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (isSelected) {
                return component;
            }

            int modelRow = table.convertRowIndexToModel(row);
            Object level = table.getModel().getValueAt(modelRow, 1);
            component.setBackground(Color.WHITE);
            if (level == Level.WARN) {
                component.setBackground(new Color(0xFFF5E6));
            } else if (level == Level.ERROR) {
                component.setBackground(new Color(0xFFF0F0));
            }
            return component;
        }
    }
}
