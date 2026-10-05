package ru.library.frontend;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.BiFunction;

final class PagedTable<T> extends VBox {
    static final int SIZE = 12;
    final TableView<T> table = Ui.table();
    final TextField search = Ui.input("Поиск");
    final Label status = Ui.label("", "muted");
    final HBox toolbar;
    final HBox actions = Ui.row();
    private final AppContext context;
    private final BiFunction<String, Integer, List<T>> fetch;
    private final Button previous, next;
    private String query = "";
    private int offset;

    PagedTable(AppContext context, String prompt, BiFunction<String, Integer, List<T>> fetch) {
        this.context = context;
        this.fetch = fetch;
        setSpacing(16);
        VBox.setVgrow(this, Priority.ALWAYS);
        search.setPromptText(prompt);
        HBox.setHgrow(search, Priority.ALWAYS);
        Button find = Ui.button("Найти", "secondary", this::search);
        toolbar = Ui.row(search, find);
        search.setOnAction(e -> search());
        previous =
                Ui.button(
                        "Назад",
                        "secondary",
                        () -> {
                            offset = Math.max(0, offset - SIZE);
                            refresh();
                        });
        next =
                Ui.button(
                        "Далее",
                        "secondary",
                        () -> {
                            offset += SIZE;
                            refresh();
                        });
        getChildren().addAll(toolbar, table, Ui.row(status, Ui.spacer(), previous, next), actions);
        actions.getStyleClass().add("action-bar");
        previous.setDisable(true);
        next.setDisable(true);
    }

    void search() {
        query = search.getText().strip();
        offset = 0;
        refresh();
    }

    void reset() {
        offset = 0;
        refresh();
    }

    void refresh() {
        String requestedQuery = query;
        int requestedOffset = offset;
        if (table.getItems().isEmpty()) status.setText("Загрузка…");
        previous.setDisable(true);
        next.setDisable(true);
        context.read(
                this,
                () -> fetch.apply(requestedQuery, requestedOffset),
                rows -> {
                    if (rows.isEmpty() && offset > 0) {
                        offset = Math.max(0, offset - SIZE);
                        refresh();
                        return;
                    }
                    table.getItems().setAll(rows.subList(0, Math.min(SIZE, rows.size())));
                    status.setText(
                            rows.isEmpty()
                                    ? "Записей не найдено"
                                    : "Показано "
                                            + (offset + 1)
                                            + "–"
                                            + (offset + table.getItems().size()));
                    previous.setDisable(offset == 0);
                    next.setDisable(rows.size() <= SIZE);
                },
                error -> {
                    previous.setDisable(offset == 0);
                    next.setDisable(true);
                    status.setText("Не удалось загрузить данные");
                    Ui.error(context.stage, error);
                });
    }
}
