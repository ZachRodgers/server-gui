package com.zach.minecraft.servergui.ui;

import com.zach.minecraft.servergui.model.LogEntry;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.table.AbstractTableModel;

public final class LogTableModel extends AbstractTableModel {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    // Strip the duplicate [HH:MM:SS LEVEL]: prefix the server embeds in its output
    private static final Pattern HEADER = Pattern.compile("^\\[\\d{2}:\\d{2}:\\d{2} [A-Z]+]: ?");

    private final List<LogEntry> entries = new ArrayList<>();
    private final String[] columns = {"Time", "Level", "Message"};

    public void add(LogEntry entry) {
        int row = entries.size();
        entries.add(entry);
        fireTableRowsInserted(row, row);
    }

    /** Return the raw LogEntry (used by the row filter for level/category checks). */
    public LogEntry getEntry(int modelRow) {
        return entries.get(modelRow);
    }

    @Override public int getRowCount()              { return entries.size(); }
    @Override public int getColumnCount()           { return columns.length; }
    @Override public String getColumnName(int col)  { return columns[col]; }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        LogEntry e = entries.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> TIME_FMT.format(e.time());
            case 1 -> e.level();
            case 2 -> HEADER.matcher(e.message()).replaceFirst("");
            default -> "";
        };
    }
}
