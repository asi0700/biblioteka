package ru.library.frontend;

import javafx.scene.layout.VBox;

import ru.library.model.Reader;

final class ReaderPicker extends VBox {
    final PagedTable<Reader> list;

    ReaderPicker(AppContext c) {
        this(c, false);
    }

    ReaderPicker(AppContext c, boolean multiple) {
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
        if (multiple) Ui.multiSelectable(list.table, Reader::id);
        else Ui.selectable(list.table);
        list.table.setPrefHeight(200);
        list.table.setMinHeight(150);
        list.actions.setVisible(false);
        list.actions.setManaged(false);
        getChildren().add(list);
        if (multiple) getChildren().add(Ui.selectionNote(list.table, "Отметьте одного или нескольких читателей", Reader::fullName));
        list.refresh();
    }

    Reader selected() {
        return Ui.singleSelected(list.table);
    }
    java.util.List<Reader> selectedReaders() { return java.util.List.copyOf(Ui.checkedItems(list.table)); }
}
