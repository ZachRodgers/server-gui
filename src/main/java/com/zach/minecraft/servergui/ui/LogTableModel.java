package com.zach.minecraft.servergui.ui;

import com.zach.minecraft.servergui.model.LogEntry;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;

public final class LogTableModel extends AbstractTableModel {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final List<LogEntry> entries = new ArrayList<>();
    private final String[] columns = {"Time", "Level", "Message"};

    public void add(LogEntry entry) {
        int row = entries.size();
        entries.add(entry);
        fireTableRowsInserted(row, row);
    }

    @Override
    public int getRowCount() {
        return entries.size();
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public String getColumnName(int column) {
        return columns[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        LogEntry entry = entries.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> TIME_FORMATTER.format(entry.time());
            case 1 -> entry.level();
            case 2 -> entry.message();
            default -> "";
        };
    }
}
