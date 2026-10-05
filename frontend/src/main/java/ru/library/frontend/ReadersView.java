package ru.library.frontend;

import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import ru.library.dto.ReaderDraft;
import ru.library.model.Reader;
import ru.library.model.ReaderTicket;

import java.time.LocalDate;
import java.util.List;

final class ReadersView {
    final VBox root;
    private final AppContext c;
    private final PagedTable<Reader> list;

    ReadersView(AppContext c) {
        this.c = c;
        root =
                Ui.page(
                        Section.READERS.title,
                        "Контакты, читательские билеты и история выдач.",
                        c.admin()
                                ? Ui.button("Добавить читателя", "primary", () -> edit(null))
                                : null);
        list =
                new PagedTable<>(
                        c,
                        "ФИО, телефон или email",
                        (query, offset) ->
                                c.backend
                                        .readers()
                                        .search(c.token, query, PagedTable.SIZE + 1, offset));
        Ui.titleColumn(list.table, "Читатель", 340, Reader::fullName, r -> "Читатель № " + r.id());
        Ui.column(list.table, "Телефон", 190, Reader::phone);
        Ui.column(list.table, "Email", 230, Reader::email);
        Ui.column(list.table, "Статус", 130, r -> r.active() ? "Активен" : "Отключён");
        list.actions
                .getChildren()
                .addAll(
                        Ui.selectionNote(
                                list.table, "Выберите читателя для действий", Reader::fullName),
                        Ui.spacer(),
                        Ui.selectedButton(
                                "Выдачи", "secondary", list.table, r -> history(r, false)),
                        Ui.selectedButton("Штрафы", "secondary", list.table, r -> history(r, true)),
                        Ui.selectedButton(
                                "Карточка читателя", "primary", list.table, this::details));
        root.getChildren().add(list);
        Ui.refreshable(root, list::refresh);
        list.refresh();
    }

    private void edit(Reader reader) {
        Ui.Form form =
                new Ui.Form(
                        c,
                        reader == null ? "Добавить читателя" : "Редактировать читателя",
                        "Сохранить");
        TextField name = form.text("ФИО *", reader == null ? "" : reader.fullName());
        TextField phone = form.text("Телефон", reader == null ? "" : reader.phone());
        TextField email = form.text("Email", reader == null ? "" : reader.email());
        form.save(
                () -> {
                    ReaderDraft draft =
                            new ReaderDraft(
                                    Ui.required(name.getText().strip(), "ФИО"),
                                    phone.getText().strip(),
                                    email.getText().strip());
                    return () -> {
                        if (reader == null) c.backend.readers().create(c.token, draft);
                        else c.backend.readers().update(c.token, reader.id(), draft);
                    };
                },
                list::refresh);
        form.show();
    }

    private void details(Reader reader) {
        c.load(
                root,
                () -> c.backend.readers().tickets(c.token, reader.id()),
                tickets -> showDetails(reader, tickets));
    }

    private void showDetails(Reader reader, List<ReaderTicket> tickets) {
        Dialog<Void> dialog = Ui.dialog(c, "Карточка читателя");
        TableView<ReaderTicket> table = Ui.table();
        table.setPrefHeight(240);
        table.getItems().setAll(tickets);
        Ui.readOnly(table);
        Ui.column(table, "Номер билета", 190, ReaderTicket::ticketNumber);
        Ui.column(table, "С", 125, t -> Ui.date(t.validFrom()));
        Ui.column(table, "По", 125, t -> Ui.date(t.validUntil()));
        Ui.column(
                table,
                "Статус",
                160,
                t -> {
                    LocalDate now = Ui.today();
                    return !t.active()
                            ? "Отключён"
                            : now.isBefore(t.validFrom())
                                    ? "Ещё не действует"
                                    : now.isAfter(t.validUntil()) ? "Истёк" : "Действует";
                });
        VBox content =
                new VBox(
                        18,
                        Ui.label(reader.fullName(), "form-title"),
                        Ui.label(
                                Ui.text(reader.phone()) + "  ·  " + Ui.text(reader.email()),
                                "muted"),
                        Ui.label("Читательские билеты", "field-label"),
                        table);
        content.setPrefWidth(640);
        if (c.admin())
            content.getChildren()
                    .addAll(
                            Ui.row(
                                    Ui.button(
                                            "Редактировать",
                                            "secondary",
                                            () -> {
                                                dialog.close();
                                                edit(reader);
                                            }),
                                    Ui.button(
                                            "Выдать билет",
                                            "primary",
                                            () -> {
                                                dialog.close();
                                                ticket(reader);
                                            })),
                            Ui.button(
                                    reader.active()
                                            ? "Отключить читателя"
                                            : "Активировать читателя",
                                    "danger",
                                    () -> {
                                        dialog.close();
                                        Ui.confirm(
                                                c,
                                                reader.active()
                                                        ? "Отключить читателя?"
                                                        : "Активировать читателя?",
                                                reader.fullName(),
                                                () ->
                                                        c.backend
                                                                .readers()
                                                                .setActive(
                                                                        c.token,
                                                                        reader.id(),
                                                                        !reader.active()),
                                                list::refresh);
                                    }));
        dialog.getDialogPane().setContent(content);
        dialog.show();
    }

    private void ticket(Reader reader) {
        Ui.Form form = new Ui.Form(c, "Выдать читательский билет", "Выдать билет");
        TextField number = form.text("Номер билета *", "");
        LocalDate today = Ui.today();
        DatePicker from = form.field("Действует с *", new DatePicker(today));
        from.setEditable(false);
        DatePicker until = form.field("Действует по *", new DatePicker(today.plusYears(1)));
        until.setEditable(false);
        form.body.getChildren().add(Ui.label("Новый билет заменит предыдущий.", "form-note"));
        form.save(
                () -> {
                    String value = Ui.required(number.getText().strip(), "Номер билета");
                    LocalDate start = Ui.required(from.getValue(), "Действует с"),
                            end = Ui.required(until.getValue(), "Действует по");
                    if (end.isBefore(start))
                        throw new IllegalArgumentException(
                                "Окончание действия должно быть не раньше начала.");
                    return () ->
                            c.backend
                                    .readers()
                                    .issueTicket(c.token, reader.id(), value, start, end);
                },
                () -> details(reader));
        form.show();
    }

    private void history(Reader reader, boolean fines) {
        Dialog<Void> dialog = Ui.dialog(c, reader.fullName());
        CirculationView view = new CirculationView(c, fines, reader);
        view.root.setPrefSize(880, 590);
        dialog.getDialogPane().setContent(view.root);
        dialog.show();
    }
}
