package com.zach.minecraft.servergui.model;

import java.time.LocalTime;

public record LogEntry(LocalTime time, Level level, String message) {
    public enum Level {
        INFO,
        WARN,
        ERROR
    }
}
