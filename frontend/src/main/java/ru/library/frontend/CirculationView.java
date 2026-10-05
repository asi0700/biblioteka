package ru.library.frontend;

import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import ru.library.dto.LibraryStatistics;
import ru.library.model.Fine;
import ru.library.model.Loan;
import ru.library.model.LoanStatus;
import ru.library.model.Reader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CirculationView {
    record LoanRow(Loan loan, String reader, String status) {}

    final VBox root;
    private final AppContext c;
    private volatile Reader reader;
    private PagedTable<LoanRow> loans;
    private PagedTable<Fine> fines;
    private final Label readerLabel = Ui.label("", "row-title");

    CirculationView(AppContext c, boolean fineMode) {
        this(c, fineMode, null);
    }

    CirculationView(AppContext c, boolean fineMode, Reader initialReader) {
        this.c = c;
        this.reader = initialReader;
        root =
                Ui.page(
                        (fineMode ? Section.FINES : Section.CIRCULATION).title,
                        fineMode
                                ? "Начисления и оплата штрафов по читателю."
                                : "Просроченные выдачи и история выбранного читателя.",
                        Ui.button("Выбрать читателя", "secondary", () -> chooseReader(fineMode)));
        readerLabel.setText(
                reader == null
                        ? fineMode
                                ? "Выберите читателя, чтобы увидеть начисления"
                                : "Все просроченные выдачи"
                        : reader.fullName());
        HBox scope = Ui.row(readerLabel, Ui.spacer());
        if (!fineMode) scope.getChildren().add(Ui.button("Сводка", "secondary", this::statistics));
        if (!fineMode)
            scope.getChildren()
                    .add(
                            Ui.button(
                                    "Все просроченные",
                                    "secondary",
                                    () -> {
                                        reader = null;
                                        readerLabel.setText("Все просроченные выдачи");
                                        loans.reset();
                                    }));
        root.getChildren().add(scope);
        if (fineMode) buildFines();
        else buildLoans();
    }

    private void statistics() {
        c.load(root, () -> c.backend.loans().statistics(c.token), this::showStatistics);
    }

    private void showStatistics(LibraryStatistics values) {
        Dialog<Void> dialog = Ui.dialog(c, "Сводка библиотеки");
        VBox content =
                new VBox(
                        18,
                        Ui.label("Сводка библиотеки", "form-title"),
                        Ui.label("Текущее состояние всей библиотеки", "muted"));
        content.setPrefWidth(460);
        String[][] rows = {
            {"Книг в каталоге", Long.toString(values.books())},
            {"Пригодных экземпляров", Long.toString(values.usableCopies())},
            {"Активных читателей", Long.toString(values.activeReaders())},
            {"Книг на руках", Long.toString(values.activeLoans())},
            {"Просроченных выдач", Long.toString(values.overdueLoans())},
            {"Неоплаченных штрафов", values.unpaidFines().toPlainString() + " ₽"}
        };
        for (String[] row : rows)
            content.getChildren()
                    .add(
                            Ui.row(
                                    Ui.label(row[0], "muted"),
                                    Ui.spacer(),
                                    Ui.label(row[1], "row-title")));
        dialog.getDialogPane().setContent(content);
        dialog.show();
    }

    private void chooseReader(boolean fineMode) {
        Ui.Form form = new Ui.Form(c, "Выбрать читателя", "Показать");
        ReaderPicker picker = new ReaderPicker(c);
        form.body.getChildren().add(picker);
        form.load(
                () -> {
                    Reader selected = Ui.required(picker.selected(), "Читатель");
                    return () -> selected;
                },
                selected -> {
                    reader = selected;
                    readerLabel.setText(reader.fullName());
                    if (fineMode) fines.reset();
                    else loans.reset();
                });
        form.show();
    }

    private void buildLoans() {
        loans = new PagedTable<>(c, "", (query, offset) -> loadLoans(reader, offset));
        loans.toolbar.setVisible(false);
        loans.toolbar.setManaged(false);
        Ui.titleColumn(
                loans.table,
                "Читатель / выдача",
                270,
                LoanRow::reader,
                r -> "Выдача № " + r.loan.id());
        Ui.column(loans.table, "Экземпляр", 105, r -> "№ " + r.loan.copyId());
        Ui.column(loans.table, "Выдана", 115, r -> Ui.date(r.loan.issueDate()));
        Ui.column(loans.table, "Вернуть до", 115, r -> Ui.date(r.loan.plannedReturnDate()));
        Ui.column(
                loans.table,
                "Статус",
                160,
                r ->
                        r.loan.actualReturnDate() != null
                                ? Ui.text(r.status) + " " + Ui.date(r.loan.actualReturnDate())
                                : r.loan.plannedReturnDate().isBefore(Ui.today())
                                        ? "Просрочена"
                                        : r.status);
        Button returned =
                Ui.selectedButton(
                        "Принять возврат",
                        "primary",
                        loans.table,
                        row -> row.loan.actualReturnDate() == null,
                        row ->
                                Ui.confirm(
                                        c,
                                        "Принять возврат?",
                                        "Экземпляр № "
                                                + row.loan.copyId()
                                                + " · "
                                                + row.reader
                                                + "\n"
                                                + "При просрочке штраф будет начислен"
                                                + " автоматически.",
                                        () -> c.backend.loans().returnBook(c.token, row.loan.id()),
                                        loans::refresh));
        loans.actions
                .getChildren()
                .addAll(
                        Ui.selectionNote(
                                loans.table,
                                "Выберите выдачу для возврата",
                                row -> "выдача № " + row.loan.id()),
                        Ui.spacer(),
                        returned);
        root.getChildren().add(loans);
        Ui.refreshable(root, loans::refresh);
        loans.refresh();
    }

    private void buildFines() {
        fines =
                new PagedTable<>(
                        c,
                        "",
                        (query, offset) -> {
                            Reader selected = reader;
                            return selected == null
                                    ? List.of()
                                    : c.backend
                                            .fines()
                                            .list(
                                                    c.token,
                                                    selected.id(),
                                                    PagedTable.SIZE + 1,
                                                    offset);
                        });
        fines.toolbar.setVisible(false);
        fines.toolbar.setManaged(false);
        Ui.column(fines.table, "Штраф", 150, f -> "№ " + f.id());
        Ui.column(fines.table, "Выдача", 150, f -> "№ " + f.loanId());
        Ui.column(fines.table, "Сумма", 160, f -> f.amount().toPlainString() + " ₽");
        Ui.column(fines.table, "Статус", 180, f -> f.paidAt() == null ? "Не оплачен" : "Оплачен");
        Ui.column(
                fines.table,
                "Дата оплаты",
                160,
                f ->
                        f.paidAt() == null
                                ? "—"
                                : Ui.date(f.paidAt().atZone(Ui.zone()).toLocalDate()));
        if (c.admin()) {
            Button pay =
                    Ui.selectedButton(
                            "Отметить оплату",
                            "primary",
                            fines.table,
                            fine -> fine.paidAt() == null,
                            fine ->
                                    Ui.confirm(
                                            c,
                                            "Подтвердить оплату?",
                                            "Штраф № "
                                                    + fine.id()
                                                    + ": "
                                                    + fine.amount().toPlainString()
                                                    + " ₽. Отметьте оплату после получения денег.",
                                            () -> c.backend.fines().pay(c.token, fine.id()),
                                            fines::refresh));
            fines.actions
                    .getChildren()
                    .addAll(
                            Ui.selectionNote(
                                    fines.table,
                                    "Выберите штраф для оплаты",
                                    fine -> "штраф № " + fine.id()),
                            Ui.spacer(),
                            pay);
        } else {
            Ui.readOnly(fines.table);
            fines.actions.getChildren().add(Ui.label("Оплату отмечает администратор", "muted"));
        }
        root.getChildren().add(fines);
        Ui.refreshable(root, fines::refresh);
        fines.refresh();
    }

    private List<LoanRow> loadLoans(Reader selected, int offset) {
        List<Loan> records =
                selected == null
                        ? c.backend.loans().overdue(c.token, PagedTable.SIZE + 1, offset)
                        : c.backend
                                .loans()
                                .history(c.token, selected.id(), PagedTable.SIZE + 1, offset);
        Map<Long, String> names = new HashMap<>();
        for (Loan loan : records) {
            names.computeIfAbsent(
                    loan.readerId(),
                    id ->
                            selected == null
                                    ? c.backend.readers().get(c.token, id).fullName()
                                    : selected.fullName());
        }
        Map<String, String> statuses = new HashMap<>();
        for (LoanStatus status : c.backend.loans().statuses(c.token))
            statuses.put(status.code(), status.name());
        return records.stream()
                .map(
                        loan ->
                                new LoanRow(
                                        loan,
                                        names.get(loan.readerId()),
                                        statuses.get(loan.statusCode())))
                .toList();
    }
}
