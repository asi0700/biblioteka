package ru.library.frontend;

import javafx.application.Application;

public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        Application.launch(LibraryApp.class, args);
    }
}
