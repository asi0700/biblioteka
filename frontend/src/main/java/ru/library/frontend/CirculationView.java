package ru.library.frontend;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import ru.library.dto.FineEntry;
import ru.library.dto.LoanEntry;
import ru.library.dto.LibraryStatistics;
import ru.library.model.LoanFilter;
import ru.library.model.Reader;
import java.util.List;

final class CirculationView {
    final VBox root;
    private final AppContext c;
    private List<Reader> readers;
    private PagedTable<LoanEntry> loans;
    private PagedTable<FineEntry> fines;
    private final Label readerLabel = Ui.label("Все читатели", "row-title");
    private LoanFilter filter = LoanFilter.ALL;
    private boolean unpaidOnly;

    CirculationView(AppContext c, boolean fineMode) {
        this(c, fineMode, List.of());
    }

    CirculationView(AppContext c, boolean fineMode, List<Reader> selected) {
        this.c = c;
        readers = List.copyOf(selected);
        root = Ui.page((fineMode ? Section.FINES : Section.CIRCULATION).title,
                fineMode ? "Начисленные штрафы за возврат книг с опозданием. Оплату подтверждает администратор."
                        : "Все выдачи библиотеки: книги на руках, просрочки и завершённые возвраты.",
                Ui.button("Выбрать читателей", "secondary", () -> chooseReaders(fineMode)));
        root.getChildren().add(Ui.label(fineMode
                ? "Штраф появляется после возврата просроченной книги: дни опоздания × ставка при выдаче. Пока книга не возвращена, смотрите её в просроченных выдачах."
                : "Выберите фильтр и при необходимости читателей. Для возврата отметьте одну или несколько выдач на руках.", "form-note"));
        HBox scope = Ui.row(readerLabel, Ui.spacer(), Ui.button("Все читатели", "secondary", () -> {
            readers = List.of(); updateReaderLabel(); reset(fineMode);
        }));
        if (!fineMode) {
            ComboBox<LoanFilter> filters = Ui.combo(List.of(LoanFilter.values()), CirculationView::filterName);
            filters.setValue(filter);
            filters.setOnAction(e -> { filter = filters.getValue(); reset(false); });
            scope.getChildren().addAll(filters, Ui.button("Статистика", "secondary", this::statistics));
        } else {
            CheckBox unpaid = new CheckBox("Только неоплаченные");
            unpaid.setOnAction(e -> { unpaidOnly = unpaid.isSelected(); fines.reset(); });
            scope.getChildren().add(unpaid);
        }
        root.getChildren().add(scope);
        updateReaderLabel();
        if (fineMode) buildFines(); else buildLoans();
    }

    private List<Long> readerIds() { return readers.stream().map(Reader::id).toList(); }

    private void updateReaderLabel() {
        readerLabel.setText(readers.isEmpty() ? "Все читатели" : readers.size() == 1
                ? readers.getFirst().fullName() : "Выбрано читателей: " + readers.size());
        readerLabel.setTooltip(new javafx.scene.control.Tooltip(readers.stream().map(Reader::fullName)
                .collect(java.util.stream.Collectors.joining("\n"))));
    }

    private void reset(boolean fineMode) {
        if (fineMode) fines.reset();
        else { Ui.clearChecked(loans.table); loans.reset(); }
    }

    private void chooseReaders(boolean fineMode) {
        Ui.Form form = new Ui.Form(c, "Выбрать читателей", "Показать выбранных");
        ReaderPicker picker = new ReaderPicker(c, true);
        form.body.getChildren().add(picker);
        form.load(() -> {
            List<Reader> selected = picker.selectedReaders();
            if (selected.isEmpty()) throw new IllegalArgumentException("Отметьте хотя бы одного читателя.");
            return () -> selected;
        }, selected -> { readers = selected; updateReaderLabel(); reset(fineMode); });
        form.show();
    }

    private void buildLoans() {
        loans = new PagedTable<>(c, "", (query, offset) ->
                c.backend.loans().entries(c.token, readerIds(), filter, PagedTable.SIZE + 1, offset));
        loans.toolbar.setVisible(false); loans.toolbar.setManaged(false);
        Ui.titleColumn(loans.table, "Книга / экземпляр", 290, LoanEntry::bookTitle,
                row -> "Экземпляр: " + row.inventoryNumber());
        Ui.column(loans.table, "Читатель", 185, LoanEntry::readerName);
        Ui.column(loans.table, "Дата выдачи", 115, row -> Ui.date(row.loan().issueDate()));
        Ui.column(loans.table, "Вернуть до", 115, row -> Ui.date(row.loan().plannedReturnDate()));
        Ui.column(loans.table, "Возврат / статус", 160, row -> row.loan().actualReturnDate() != null
                ? "Возвращена " + Ui.date(row.loan().actualReturnDate())
                : row.loan().plannedReturnDate().isBefore(Ui.today()) ? "Просрочена" : "На руках");
        Ui.multiSelectable(loans.table, row -> row.loan().id());
        loans.actions.getChildren().addAll(
                Ui.selectionNote(loans.table, "Отметьте выдачи для возврата", LoanEntry::bookTitle), Ui.spacer(),
                Ui.button("Снять выбор", "secondary", () -> Ui.clearChecked(loans.table)),
                Ui.groupButton("Вернуть выбранные книги", "primary", loans.table,
                        selected -> selected.stream().allMatch(row -> row.loan().actualReturnDate() == null),
                        selected -> Ui.confirm(c, "Вернуть выбранные книги?", "Выбрано книг: " + selected.size()
                                + "\n" + selected.stream().map(row -> row.bookTitle() + " · " + row.readerName()
                                + " · " + row.inventoryNumber()).collect(java.util.stream.Collectors.joining("\n"))
                                + "\nПри просрочке штраф будет начислен автоматически.",
                                () -> c.backend.loans().returnBooks(c.token, selected.stream().map(row -> row.loan().id()).toList()),
                                () -> { Ui.clearChecked(loans.table); loans.refresh(); })));
        loans.actions.getChildren().getLast().setAccessibleHelp("Можно вернуть сразу несколько книг на руках. Уже возвращённые выдачи повторно вернуть нельзя.");
        root.getChildren().add(loans);
        Ui.refreshable(root, loans::refresh);
        loans.refresh();
    }

    private void buildFines() {
        fines = new PagedTable<>(c, "", (query, offset) ->
                c.backend.fines().entries(c.token, readerIds(), unpaidOnly, PagedTable.SIZE + 1, offset));
        fines.toolbar.setVisible(false); fines.toolbar.setManaged(false);
        fines.table.setPlaceholder(Ui.label("Начисленных штрафов по этому выбору нет", "muted"));
        Ui.titleColumn(fines.table, "Читатель / книга", 340, FineEntry::readerName, FineEntry::bookTitle);
        Ui.column(fines.table, "Экземпляр", 170, FineEntry::inventoryNumber);
        Ui.column(fines.table, "Сумма штрафа", 135, row -> row.fine().amount().toPlainString() + " ₽");
        Ui.column(fines.table, "Оплата", 125, row -> row.fine().paidAt() == null ? "Не оплачен" : "Оплачен");
        Ui.column(fines.table, "Дата оплаты", 135, row -> row.fine().paidAt() == null ? "—"
                : Ui.date(row.fine().paidAt().atZone(Ui.zone()).toLocalDate()));
        if (c.admin()) fines.actions.getChildren().addAll(
                Ui.selectionNote(fines.table, "Отметьте штраф для оплаты", FineEntry::readerName), Ui.spacer(),
                Ui.selectedButton("Подтвердить оплату", "primary", fines.table, row -> row.fine().paidAt() == null,
                        row -> Ui.confirm(c, "Подтвердить оплату?", row.readerName() + ": "
                                + row.fine().amount().toPlainString() + " ₽. Подтвердите после получения денег.",
                                () -> c.backend.fines().pay(c.token, row.fine().id()), fines::refresh)));
        else {
            Ui.readOnly(fines.table);
            fines.actions.getChildren().add(Ui.label("Оплату подтверждает администратор", "muted"));
        }
        root.getChildren().add(fines);
        Ui.refreshable(root, fines::refresh);
        fines.refresh();
    }

    private static String filterName(LoanFilter value) {
        return switch (value) {
            case ALL -> "Все выдачи";
            case ACTIVE -> "Книги на руках";
            case OVERDUE -> "Только просроченные";
            case RETURNED -> "Возвращённые книги";
        };
    }

    private void statistics() {
        c.load(root, () -> c.backend.loans().statistics(c.token), this::showStatistics);
    }

    private void showStatistics(LibraryStatistics values) {
        Dialog<Void> dialog = Ui.dialog(c, "Статистика библиотеки");
        VBox content = new VBox(18, Ui.label("Статистика библиотеки", "form-title"));
        content.setPrefWidth(460);
        String[][] rows = {
                {"Книг в каталоге", Long.toString(values.books())},
                {"Пригодных экземпляров", Long.toString(values.usableCopies())},
                {"Активных читателей", Long.toString(values.activeReaders())},
                {"Книг на руках", Long.toString(values.activeLoans())},
                {"Просроченных выдач", Long.toString(values.overdueLoans())},
                {"Сумма неоплаченных штрафов", values.unpaidFines().toPlainString() + " ₽"}
        };
        for (String[] row : rows) content.getChildren().add(
                Ui.row(Ui.label(row[0], "muted"), Ui.spacer(), Ui.label(row[1], "row-title")));
        dialog.getDialogPane().setContent(content);
        dialog.show();
    }
}
