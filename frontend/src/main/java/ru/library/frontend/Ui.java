package ru.library.frontend;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CheckBox;
import javafx.collections.ObservableList;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class Ui {
    private static final Object REFRESH_ACTION = new Object();
    private static final Object CHECKED_ROWS = new Object();

    private Ui() {}

    static void refreshable(Node root, Runnable refresh) {
        root.getProperties().put(REFRESH_ACTION, refresh);
    }

    static void refresh(Node root) {
        if (root.getProperties().get(REFRESH_ACTION) instanceof Runnable action) action.run();
        else if (root instanceof ScrollPane scroll) refresh(scroll.getContent());
    }

    static String text(Object value) {
        return value == null || value.toString().isBlank() ? "—" : value.toString();
    }

    static String date(LocalDate value) {
        return value == null ? "—" : value.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }

    static ZoneId zone() {
        String value = System.getenv("LIBRARY_ZONE");
        return ZoneId.of(value == null || value.isBlank() ? "Europe/Moscow" : value);
    }

    static LocalDate today() {
        return LocalDate.now(zone());
    }

    static Label label(String text, String style) {
        Label l = new Label(text);
        l.getStyleClass().add(style);
        if ("form-note".equals(style)) {
            l.setWrapText(true);
            l.setMaxWidth(Double.MAX_VALUE);
            l.setMinHeight(Region.USE_PREF_SIZE);
        }
        return l;
    }

    static Button button(String text, String style, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add(style);
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> action.run());
        return b;
    }

    static Region spacer() {
        Region s = new Region();
        HBox.setHgrow(s, Priority.ALWAYS);
        VBox.setVgrow(s, Priority.ALWAYS);
        return s;
    }

    static HBox row(Node... nodes) {
        HBox row = new HBox(12, nodes);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    static VBox page(String title, String description, Node action) {
        VBox heading = new VBox(6, label(title, "page-title"), label(description, "muted"));
        HBox top = row(heading, spacer());
        heading.setMinWidth(0);
        ((Label) heading.getChildren().getFirst()).setWrapText(true);
        ((Label) heading.getChildren().getLast()).setWrapText(true);
        ((Label) heading.getChildren().getLast()).setMaxWidth(Double.MAX_VALUE);
        ((Label) heading.getChildren().getLast()).setMinHeight(Region.USE_PREF_SIZE);
        HBox.setHgrow(heading, Priority.ALWAYS);
        if (action != null) top.getChildren().add(action);
        top.getChildren().add(button("О странице", "secondary", () -> {
            Window owner = top.getScene() == null ? null : top.getScene().getWindow();
            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Как пользоваться: " + title);
            style(dialog, owner);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            ((Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE)).setText("Закрыть");
            Label help = label(PageHelp.text(title), "form-note");
            help.setWrapText(true);
            help.setMaxWidth(Double.MAX_VALUE);
            ScrollPane scroll = new ScrollPane(help);
            scroll.setFitToWidth(true);
            scroll.setPrefViewportWidth(550);
            scroll.setPrefViewportHeight(360);
            dialog.getDialogPane().setContent(scroll);
            dialog.show();
        }));
        VBox page = new VBox(24, top);
        page.getStyleClass().add("page");
        return page;
    }

    static String recordCount(int count) {
        int lastTwo = count % 100, last = count % 10;
        String word = lastTwo >= 11 && lastTwo <= 14 ? "записей"
                : last == 1 ? "запись" : last >= 2 && last <= 4 ? "записи" : "записей";
        return count + " " + word;
    }

    static ScrollPane scrollable(Node node) {
        ScrollPane scroll = new ScrollPane(node);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        return scroll;
    }

    static TextField input(String prompt) {
        TextField t = new TextField();
        t.setPromptText(prompt);
        t.setMaxWidth(Double.MAX_VALUE);
        return t;
    }

    static <T> TableView<T> table() {
        TableView<T> table = new TableView<>();
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(label("Здесь пока нет записей", "muted"));
        table.setFixedCellSize(66);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setMinHeight(150);
        return table;
    }

    static <T> void column(TableView<T> table, String name, double width, Function<T, ?> value) {
        TableColumn<T, String> column = new TableColumn<>(name);
        column.setPrefWidth(width);
        column.setMinWidth(65);
        column.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(text(value.apply(cell.getValue()))));
        column.setCellFactory(
                c ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(empty ? null : item);
                                setTooltip(empty || item == null ? null : new Tooltip(item));
                            }
                        });
        column.setSortable(false);
        table.getColumns().add(column);
    }

    static <T> void titleColumn(
            TableView<T> table,
            String name,
            double width,
            Function<T, String> title,
            Function<T, String> subtitle) {
        TableColumn<T, T> column = new TableColumn<>(name);
        column.setPrefWidth(width);
        column.setMinWidth(180);
        column.setSortable(false);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setCellFactory(
                c ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(T item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(null);
                                if (empty || item == null) {
                                    setGraphic(null);
                                    return;
                                }
                                Label main = label(title.apply(item), "row-title");
                                Label sub = label(subtitle.apply(item), "row-subtitle");
                                main.setMaxWidth(Double.MAX_VALUE);
                                sub.setMaxWidth(Double.MAX_VALUE);
                                VBox box = new VBox(3, main, sub);
                                box.setAlignment(Pos.CENTER_LEFT);
                                setGraphic(box);
                                setTooltip(
                                        new Tooltip(
                                                title.apply(item) + "\n" + subtitle.apply(item)));
                            }
                        });
        table.getColumns().add(column);
    }

    static <T> Button selectedButton(
            String title, String style, TableView<T> table, Consumer<T> action) {
        return selectedButton(title, style, table, item -> true, action);
    }

    static <T> Button selectedButton(
            String title,
            String style,
            TableView<T> table,
            Predicate<T> enabled,
            Consumer<T> action) {
        selectable(table);
        Button b =
                button(
                        title,
                        style,
                        () -> {
                            T value = singleSelected(table);
                            if (value != null && enabled.test(value)) action.accept(value);
                        });
        b.disableProperty()
                .bind(
                        Bindings.createBooleanBinding(
                                () -> {
                                    T value = singleSelected(table);
                                    return value == null || !enabled.test(value);
                                },
                                table.getSelectionModel().selectedItemProperty(), checkedItems(table)));
        if (table.getProperties().containsKey(CHECKED_ROWS))
            b.setTooltip(new Tooltip("Для этого действия отметьте ровно одного читателя"));
        return b;
    }

    static <T> void selectable(TableView<T> table) {
        if (table.getStyleClass().contains("selectable-table")) return;
        table.getStyleClass().add("selectable-table");
        TableColumn<T, T> choice = new TableColumn<>("");
        choice.setMinWidth(40);
        choice.setMaxWidth(40);
        choice.setPrefWidth(40);
        choice.setSortable(false);
        choice.setReorderable(false);
        choice.setResizable(false);
        choice.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        choice.setCellFactory(
                column ->
                        new TableCell<>() {
                            final CheckBox marker = new CheckBox();

                            {
                                setAlignment(Pos.CENTER);
                                getStyleClass().add("choice-cell");
                                marker.setAccessibleText("Выбрать строку");
                                marker.setOnAction(e -> {
                                    if (getItem() == null) return;
                                    if (marker.isSelected()) table.getSelectionModel().select(getItem());
                                    else table.getSelectionModel().clearSelection(getIndex());
                                });
                                table.getSelectionModel().selectedItemProperty().addListener((o, a, b) ->
                                        marker.setSelected(getItem() != null && getItem().equals(b)));
                            }

                            @Override
                            protected void updateItem(T item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(empty ? null : marker);
                                marker.setSelected(!empty && item != null && item.equals(table.getSelectionModel().getSelectedItem()));
                                setAccessibleText(empty ? "" : "Выбор строки");
                            }
                        });
        table.getColumns().addFirst(choice);
    }

    static void readOnly(TableView<?> table) {
        table.setSelectionModel(null);
    }

    static <T> Label selectionNote(TableView<T> table, String prompt, Function<T, String> name) {
        Label note = label(prompt, "muted");
        note.setMinWidth(0);
        note.setMaxWidth(320);
        checkedItems(table).addListener((javafx.collections.ListChangeListener<T>) changes -> {
            var items = checkedItems(table);
            note.setText(items.isEmpty() ? prompt : items.size() == 1
                    ? "Выбрано: " + name.apply(items.getFirst()) : "Выбрано записей: " + items.size());
            note.setTooltip(new Tooltip(note.getText()));
        });
        return note;
    }

    static <T> ComboBox<T> combo(List<T> values, Function<T, String> display) {
        return buildCombo(values, display, "");
    }

    static <T> ComboBox<T> optionalCombo(List<T> values, Function<T, String> display) {
        List<T> choices = new ArrayList<>();
        choices.add(null);
        choices.addAll(values);
        return buildCombo(choices, display, "Не указано");
    }

    private static <T> ComboBox<T> buildCombo(
            List<T> values, Function<T, String> display, String emptyLabel) {
        ComboBox<T> box = new ComboBox<>(FXCollections.observableArrayList(values));
        box.setPromptText(emptyLabel);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setConverter(
                new StringConverter<>() {
                    public String toString(T value) {
                        return value == null ? emptyLabel : display.apply(value);
                    }

                    public T fromString(String value) {
                        return box.getItems().stream()
                                .filter(item -> item != null && display.apply(item).equals(value))
                                .findFirst()
                                .orElse(null);
                    }
                });
        return box;
    }

    static Dialog<Void> dialog(AppContext context, String title) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        style(dialog, context.stage);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE)).setText("Закрыть");
        return dialog;
    }

    static void information(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        style(alert, owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.show();
    }

    private static final class CheckedRows<T> {
        final ObservableList<T> items = FXCollections.observableArrayList();
        final java.util.Map<Long, T> selected = new java.util.LinkedHashMap<>();
        final Function<T, Long> key;
        CheckedRows(Function<T, Long> key) { this.key = key; }
        void toggle(T item, boolean checked) {
            if (checked) selected.put(key.apply(item), item);
            else selected.remove(key.apply(item));
            items.setAll(selected.values());
        }
    }

    @SuppressWarnings("unchecked")
    static <T> ObservableList<T> checkedItems(TableView<T> table) {
        if (table.getProperties().get(CHECKED_ROWS) instanceof CheckedRows<?> checked)
            return ((CheckedRows<T>) checked).items;
        return table.getSelectionModel().getSelectedItems();
    }

    static <T> T singleSelected(TableView<T> table) {
        var items = checkedItems(table);
        return items.size() == 1 ? items.getFirst() : null;
    }

    static void clearChecked(TableView<?> table) {
        if (table.getProperties().get(CHECKED_ROWS) instanceof CheckedRows<?> checked) {
            checked.selected.clear();
            checked.items.clear();
        }
        table.getSelectionModel().clearSelection();
    }

    static <T> void multiSelectable(TableView<T> table, Function<T, Long> key) {
        if (table.getProperties().containsKey(CHECKED_ROWS)) return;
        table.getStyleClass().add("selectable-table");
        CheckedRows<T> checked = new CheckedRows<>(key);
        table.getProperties().put(CHECKED_ROWS, checked);
        table.setRowFactory(view -> {
            javafx.scene.control.TableRow<T> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(event -> {
                if (row.isEmpty()) return;
                for (Node target = event.getTarget() instanceof Node node ? node : null; target != null; target = target.getParent())
                    if (target instanceof CheckBox) return;
                checked.toggle(row.getItem(), !checked.selected.containsKey(key.apply(row.getItem())));
            });
            return row;
        });
        TableColumn<T, T> choice = new TableColumn<>("");
        choice.setMinWidth(44); choice.setMaxWidth(44); choice.setPrefWidth(44);
        choice.setSortable(false); choice.setReorderable(false); choice.setResizable(false);
        choice.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        choice.setCellFactory(column -> new TableCell<>() {
            final CheckBox box = new CheckBox();
            {
                setAlignment(Pos.CENTER);
                box.setAccessibleText("Добавить запись к выбору");
                box.setOnAction(e -> { if (getItem() != null) checked.toggle(getItem(), box.isSelected()); });
                checked.items.addListener((javafx.collections.ListChangeListener<T>) changes -> updateCheck());
            }
            private void updateCheck() {
                box.setSelected(getItem() != null && checked.selected.containsKey(key.apply(getItem())));
            }
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
                updateCheck();
            }
        });
        table.getColumns().addFirst(choice);
    }

    static <T> Button groupButton(String title, String style, TableView<T> table, Consumer<List<T>> action) {
        return groupButton(title, style, table, items -> true, action);
    }

    static <T> Button groupButton(String title, String style, TableView<T> table,
                                 Predicate<List<T>> enabled, Consumer<List<T>> action) {
        Button button = button(title, style, () -> action.accept(List.copyOf(checkedItems(table))));
        button.disableProperty().bind(Bindings.createBooleanBinding(() ->
                checkedItems(table).isEmpty() || !enabled.test(List.copyOf(checkedItems(table))), checkedItems(table)));
        return button;
    }

    static void error(Window owner, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        style(alert, owner);
        alert.setTitle("Не удалось выполнить действие");
        alert.setHeaderText(null);
        alert.show();
    }

    static void confirm(
            AppContext c, String title, String message, Runnable action, Runnable done) {
        Form form = new Form(c, title, "Подтвердить");
        form.body.getChildren().add(label(message, "form-note"));
        form.save(() -> action, done);
        form.show();
    }

    static void style(Dialog<?> dialog, Window owner) {
        dialog.initOwner(owner);
        dialog.getDialogPane().getStylesheets().add(stylesheet());
        dialog.getDialogPane().getStyleClass().add("library-dialog");
        dialog.setHeaderText(null);
    }

    static String stylesheet() {
        var resource = Ui.class.getResource("library.css");
        if (resource == null) throw new IllegalStateException("Не найден файл оформления library.css");
        return resource.toExternalForm();
    }

    static <T> T required(T value, String name) {
        if (value == null || value instanceof String s && s.isBlank())
            throw new IllegalArgumentException("Заполните поле «" + name + "».");
        return value;
    }

    static Integer year(String value) {
        if (value.isBlank()) return null;
        try {
            int year = Integer.parseInt(value.strip());
            if (year < 1 || year > 9999) throw new NumberFormatException();
            return year;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Год должен быть числом от 1 до 9999.");
        }
    }

    static final class Form extends Dialog<Void> {
        final VBox body = new VBox(15);
        final Label feedback = label("", "error-text");
        private final AppContext context;
        private final ButtonType submit;

        Form(AppContext context, String title, String action) {
            this.context = context;
            setTitle(title);
            style(this, context.stage);
            body.getChildren().add(label(title, "form-title"));
            body.setPadding(new Insets(10, 4, 14, 4));
            ScrollPane scroll = new ScrollPane(body);
            scroll.setFitToWidth(true);
            scroll.setPrefViewportWidth(510);
            scroll.setMaxHeight(590);
            feedback.setWrapText(true);
            feedback.setVisible(false);
            feedback.setManaged(false);
            getDialogPane().setContent(new VBox(12, scroll, feedback));
            submit = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
            getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, submit);
            ((Button) getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Отмена");
            getDialogPane().lookupButton(submit).getStyleClass().add("primary");
        }

        <N extends Node> N field(String title, N control) {
            body.getChildren().add(new VBox(6, label(title, "field-label"), control));
            return control;
        }

        TextField text(String title, String value) {
            TextField input = Ui.input(title);
            input.setText(value == null ? "" : value);
            return field(title, input);
        }

        void problem(String text) {
            feedback.setText(text);
            feedback.setVisible(true);
            feedback.setManaged(true);
        }

        void save(Supplier<Runnable> prepare, Runnable done) {
            submit(
                    () -> {
                        Runnable action = prepare.get();
                        return () -> {
                            action.run();
                            return null;
                        };
                    },
                    ignored -> done.run(),
                    true);
        }

        <T> void load(Supplier<Callable<T>> prepare, Consumer<T> done) {
            submit(prepare, done, false);
        }

        private <T> void submit(
                Supplier<Callable<T>> prepare, Consumer<T> done, boolean changesData) {
            getDialogPane()
                    .lookupButton(submit)
                    .addEventFilter(
                            ActionEvent.ACTION,
                            event -> {
                                event.consume();
                                try {
                                    Callable<T> action = prepare.get();
                                    feedback.setVisible(false);
                                    feedback.setManaged(false);
                                    setOnCloseRequest(
                                            e -> {
                                                if (getDialogPane().isDisabled()) e.consume();
                                            });
                                    context.execute(
                                            getDialogPane(),
                                            action,
                                            result -> {
                                                if (changesData) context.dataChanged();
                                                close();
                                                done.accept(result);
                                            },
                                            this::problem);
                                } catch (IllegalArgumentException ex) {
                                    problem(ex.getMessage());
                                }
                            });
        }
    }
}
