package com.zach.minecraft.servergui.model;

import java.time.LocalTime;

public record LogEntry(LocalTime time, Level level, Category category, String message) {

    public enum Level    { INFO, WARN, ERROR }
    public enum Category { SYSTEM, CHAT, COMMAND }

    /** Convenience constructor — defaults category to SYSTEM. */
    public LogEntry(LocalTime time, Level level, String message) {
        this(time, level, Category.SYSTEM, message);
    }
}
