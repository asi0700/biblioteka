package ru.library.frontend;

import javafx.scene.layout.VBox;

import ru.library.model.Reader;

final class ReaderPicker extends VBox {
    final PagedTable<Reader> list;

    ReaderPicker(AppContext c) {
        list =
                new PagedTable<>(
                        c,
                        "ФИО, телефон или email",
                        (query, offset) ->
                                c.backend
                                        .readers()
                                        .search(c.token, query, PagedTable.SIZE + 1, offset));
        Ui.titleColumn(
                list.table,
                "Читатель",
                350,
                Reader::fullName,
                r ->
                        "№ "
                                + r.id()
                                + " · "
                                + Ui.text(r.phone())
                                + (r.active() ? "" : " · Отключён"));
        Ui.selectable(list.table);
        list.table.setPrefHeight(200);
        list.table.setMinHeight(150);
        list.actions.setVisible(false);
        list.actions.setManaged(false);
        getChildren().add(list);
        list.refresh();
    }

    Reader selected() {
        return list.table.getSelectionModel().getSelectedItem();
    }
}
