package ru.library.frontend;

import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import ru.library.dto.BookDraft;
import ru.library.dto.DictionaryEntry;
import ru.library.model.Book;
import ru.library.model.BookCopy;
import ru.library.model.CopyCondition;
import ru.library.model.DictionaryKind;
import ru.library.model.Reader;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class CatalogView {
    record Row(Book book, String authors, long available) {}

    final VBox root;
    final PagedTable<Row> list;
    private final AppContext c;

    CatalogView(AppContext c) {
        this.c = c;
        root =
                Ui.page(
                        "Каталог книг",
                        "Книги, экземпляры и доступность в библиотеке.",
                        c.admin()
                                ? Ui.button("Добавить книгу", "primary", () -> edit(null))
                                : null);
        list =
                new PagedTable<>(
                        c,
                        "Название, автор или ISBN",
                        (query, offset) ->
                                c
                                        .backend
                                        .books()
                                        .catalog(c.token, query, PagedTable.SIZE + 1, offset)
                                        .stream()
                                        .map(
                                                entry ->
                                                        new Row(
                                                                entry.book(),
                                                                entry.authors(),
                                                                entry.available()))
                                        .toList());
        Ui.titleColumn(
                list.table,
                "Название",
                400,
                r -> r.book.title(),
                r -> r.authors.isBlank() ? "Автор не указан" : r.authors);
        Ui.column(list.table, "ISBN", 215, r -> r.book.isbn());
        Ui.column(list.table, "Год", 85, r -> r.book.publicationYear());
        Ui.column(
                list.table,
                "Доступно",
                155,
                r -> r.available == 0 ? "Нет в наличии" : r.available + " экз.");
        list.table.getColumns().getLast().setMinWidth(145);
        list.toolbar.getChildren().add(Ui.button("По номеру", "secondary", this::openByNumber));
        Label selection =
                Ui.selectionNote(
                        list.table, "Выберите книгу для действий", row -> row.book.title());
        Button issue =
                Ui.selectedButton(
                        "Выдать книгу",
                        "primary",
                        list.table,
                        row -> row.available > 0,
                        r -> issue(c, r.book, list::refresh));
        list.actions
                .getChildren()
                .addAll(
                        selection,
                        Ui.spacer(),
                        Ui.selectedButton(
                                "Подробнее", "secondary", list.table, r -> details(r.book)),
                        issue);
        root.getChildren().add(list);
        Ui.refreshable(root, list::refresh);
        list.refresh();
    }

    private void openByNumber() {
        Ui.Form form = new Ui.Form(c, "Открыть книгу по номеру", "Открыть");
        TextField number = form.text("Номер книги", "");
        form.body
                .getChildren()
                .add(
                        Ui.label(
                                "По номеру можно открыть и архивную книгу. Номер указан в карточке"
                                        + " книги.",
                                "form-note"));
        form.load(
                () -> {
                    long id;
                    try {
                        id = Long.parseLong(number.getText().strip());
                        if (id <= 0) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                "Введите положительный целый номер книги.");
                    }
                    return () -> c.backend.books().get(c.token, id);
                },
                this::details);
        form.show();
    }

    private void details(Book book) {
        c.load(
                root,
                () -> c.backend.books().copies(c.token, book.id()),
                copies -> showDetails(book, copies));
    }

    private void showDetails(Book book, List<BookCopy> copies) {
        Dialog<Void> dialog = Ui.dialog(c, book.title());
        TableView<BookCopy> table = Ui.table();
        table.getItems().setAll(copies);
        table.setPrefHeight(270);
        Ui.column(table, "Инвентарный номер", 290, BookCopy::inventoryNumber);
        Ui.column(table, "Состояние", 190, copy -> conditionName(copy.condition()));
        Label description = Ui.label(Ui.text(book.description()), "muted");
        description.setWrapText(true);
        VBox content =
                new VBox(
                        18,
                        Ui.label(book.title(), "form-title"),
                        Ui.label(
                                "Книга № "
                                        + book.id()
                                        + (book.archived() ? " · В архиве" : " · В каталоге"),
                                "muted"),
                        description,
                        Ui.label("Экземпляры", "field-label"),
                        table);
        content.setPrefWidth(590);
        if (c.admin()) {
            HBox actions = Ui.row();
            if (!book.archived())
                actions.getChildren()
                        .add(
                                Ui.button(
                                        "Добавить экземпляр",
                                        "secondary",
                                        () -> {
                                            dialog.close();
                                            addCopy(book);
                                        }));
            actions.getChildren()
                    .add(
                            Ui.selectedButton(
                                    "Состояние",
                                    "secondary",
                                    table,
                                    copy -> {
                                        dialog.close();
                                        changeCondition(book, copy);
                                    }));
            HBox edit =
                    Ui.row(
                            Ui.button(
                                    "Редактировать книгу",
                                    "secondary",
                                    () -> {
                                        dialog.close();
                                        edit(book);
                                    }),
                            Ui.spacer(),
                            Ui.button(
                                    book.archived() ? "Восстановить" : "В архив",
                                    book.archived() ? "primary" : "danger",
                                    () -> {
                                        dialog.close();
                                        Ui.confirm(
                                                c,
                                                book.archived()
                                                        ? "Восстановить книгу?"
                                                        : "Архивировать книгу?",
                                                book.archived()
                                                        ? "Книга снова появится в каталоге."
                                                        : "Книга исчезнет из каталога. История"
                                                                + " выдач сохранится.\n"
                                                                + "Для восстановления используйте"
                                                                + " номер книги: "
                                                                + book.id(),
                                                () ->
                                                        c.backend
                                                                .books()
                                                                .archive(
                                                                        c.token,
                                                                        book.id(),
                                                                        !book.archived()),
                                                list::refresh);
                                    }));
            content.getChildren().addAll(actions, edit);
        } else Ui.readOnly(table);
        dialog.getDialogPane().setContent(content);
        dialog.show();
    }

    private void addCopy(Book book) {
        Ui.Form form = new Ui.Form(c, "Новый экземпляр", "Добавить");
        TextField inventory = form.text("Инвентарный номер *", "");
        form.save(
                () -> {
                    String number = Ui.required(inventory.getText().strip(), "Инвентарный номер");
                    return () -> c.backend.books().addCopy(c.token, book.id(), number);
                },
                () -> {
                    list.refresh();
                    details(book);
                });
        form.show();
    }

    private void changeCondition(Book book, BookCopy copy) {
        Ui.Form form = new Ui.Form(c, "Состояние экземпляра", "Сохранить");
        ComboBox<CopyCondition> condition =
                form.field(
                        "Состояние",
                        Ui.combo(List.of(CopyCondition.values()), CatalogView::conditionName));
        condition.setValue(copy.condition());
        form.save(
                () -> {
                    CopyCondition value = Ui.required(condition.getValue(), "Состояние");
                    return () -> c.backend.books().setCopyCondition(c.token, copy.id(), value);
                },
                () -> {
                    list.refresh();
                    details(book);
                });
        form.show();
    }

    static String conditionName(CopyCondition value) {
        return switch (value) {
            case USABLE -> "Пригоден";
            case REPAIR -> "На ремонте";
            case RETIRED -> "Списан";
        };
    }

    private void edit(Book book) {
        c.load(root, this::loadDictionaries, dictionaries -> showBookForm(book, dictionaries));
    }

    private Map<DictionaryKind, List<DictionaryEntry>> loadDictionaries() {
        Map<DictionaryKind, List<DictionaryEntry>> values = new EnumMap<>(DictionaryKind.class);
        for (DictionaryKind kind : DictionaryKind.values())
            values.put(kind, c.backend.dictionaries().list(c.token, kind));
        return values;
    }

    private void showBookForm(Book book, Map<DictionaryKind, List<DictionaryEntry>> dictionaries) {
        Ui.Form form =
                new Ui.Form(
                        c, book == null ? "Добавить книгу" : "Редактировать книгу", "Сохранить");
        TextField title = form.text("Название *", book == null ? "" : book.title());
        TextField isbn = form.text("ISBN", book == null ? "" : book.isbn());
        TextField year =
                form.text(
                        "Год издания",
                        book == null || book.publicationYear() == null
                                ? ""
                                : book.publicationYear().toString());
        ListView<DictionaryEntry> authors =
                new ListView<>(
                        FXCollections.observableArrayList(dictionaries.get(DictionaryKind.AUTHOR)));
        authors.setCellFactory(
                v ->
                        new ListCell<>() {
                            protected void updateItem(DictionaryEntry item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(empty || item == null ? null : item.name());
                            }
                        });
        authors.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        authors.setPrefHeight(125);
        if (book != null)
            for (DictionaryEntry author : authors.getItems())
                if (book.authorIds().contains(author.id()))
                    authors.getSelectionModel().select(author);
        form.field("Авторы · Ctrl для выбора нескольких", authors);
        ComboBox<DictionaryEntry> genre =
                dictionaryField(
                        form,
                        "Жанр",
                        dictionaries.get(DictionaryKind.GENRE),
                        book == null ? null : book.genreId());
        ComboBox<DictionaryEntry> publisher =
                dictionaryField(
                        form,
                        "Издательство",
                        dictionaries.get(DictionaryKind.PUBLISHER),
                        book == null ? null : book.publisherId());
        TextArea description = new TextArea(book == null ? "" : book.description());
        description.setWrapText(true);
        description.setPrefRowCount(3);
        form.field("Описание", description);
        form.save(
                () -> {
                    BookDraft draft =
                            new BookDraft(
                                    Ui.required(title.getText().strip(), "Название"),
                                    isbn.getText().strip(),
                                    Ui.year(year.getText()),
                                    id(genre.getValue()),
                                    id(publisher.getValue()),
                                    description.getText(),
                                    authors.getSelectionModel().getSelectedItems().stream()
                                            .map(DictionaryEntry::id)
                                            .toList());
                    return () -> {
                        if (book == null) c.backend.books().create(c.token, draft);
                        else c.backend.books().update(c.token, book.id(), draft);
                    };
                },
                list::refresh);
        form.show();
    }

    private static ComboBox<DictionaryEntry> dictionaryField(
            Ui.Form form, String name, List<DictionaryEntry> values, Long selected) {
        ComboBox<DictionaryEntry> combo =
                form.field(name, Ui.optionalCombo(values, DictionaryEntry::name));
        combo.setValue(
                values.stream()
                        .filter(v -> selected != null && v.id() == selected)
                        .findFirst()
                        .orElse(null));
        return combo;
    }

    private static Long id(DictionaryEntry entry) {
        return entry == null ? null : entry.id();
    }

    static void issue(AppContext c, Book book, Runnable done) {
        Ui.Form form = new Ui.Form(c, "Выдать книгу", "Подтвердить выдачу");
        form.body.getChildren().add(Ui.label(book.title(), "row-title"));
        ReaderPicker picker = new ReaderPicker(c);
        form.field("Читатель *", picker);
        form.body
                .getChildren()
                .add(Ui.label("Срок возврата определяется правилами библиотеки.", "form-note"));
        form.save(
                () -> {
                    Reader reader = Ui.required(picker.selected(), "Читатель");
                    return () -> c.backend.loans().issue(c.token, book.id(), reader.id());
                },
                done);
        form.show();
    }
}
