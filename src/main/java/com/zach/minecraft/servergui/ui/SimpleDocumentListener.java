package com.zach.minecraft.servergui.ui;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

@FunctionalInterface
public interface SimpleDocumentListener extends DocumentListener {
    void update();

    static SimpleDocumentListener onChange(Runnable runnable) {
        return runnable::run;
    }

    @Override
    default void insertUpdate(DocumentEvent event) {
        update();
    }

    @Override
    default void removeUpdate(DocumentEvent event) {
        update();
    }

    @Override
    default void changedUpdate(DocumentEvent event) {
        update();
    }
}
