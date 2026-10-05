package ru.library.frontend;

import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

enum Icon {
    BOOK(
            "M12 5 C8 2 4 3 2 4 L2 20 C5 18 9 18 12 21 C15 18 19 18 22 20 L22 4 C18 2 15 3 12 5 L12"
                    + " 21"),
    PEOPLE(
            "M15 7 A4 4 0 1 1 7 7 A4 4 0 1 1 15 7 M3 21 L3 19 C3 13 19 13 19 19 L19 21 M19 4 C23 5"
                    + " 23 10 19 11 M22 21 L22 18 C22 16 21 15 20 15"),
    CIRCULATION("M3 7 L21 7 M17 3 L21 7 L17 11 M21 17 L3 17 M7 13 L3 17 L7 21"),
    FINES("M6 2 L15 2 L20 7 L20 22 L4 22 L4 2 Z M14 2 L14 8 L20 8 M8 12 L16 12 M8 16 L16 16"),
    FOLDER("M2 6 L10 6 L12 8 L22 8 L22 21 L2 21 Z M2 6 L2 4 L9 4 L11 6"),
    SETTINGS("M4 7 L20 7 M4 17 L20 17 M9 4 L9 10 M15 14 L15 20");

    private final String data;

    Icon(String data) {
        this.data = data;
    }

    StackPane createNode() {
        SVGPath path = new SVGPath();
        path.setContent(data);
        path.setFill(null);
        path.setStroke(Color.web("#475549"));
        path.setStrokeWidth(1.7);
        path.setStrokeLineCap(StrokeLineCap.ROUND);
        path.setStrokeLineJoin(StrokeLineJoin.ROUND);
        path.setScaleX(0.8);
        path.setScaleY(0.8);
        StackPane box = new StackPane(new Group(path));
        box.setMinSize(22, 22);
        box.setPrefSize(22, 22);
        box.setMaxSize(22, 22);
        return box;
    }
}
