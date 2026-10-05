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

    private Ui() {}

    static void refreshable(Node root, Runnable refresh) {
        root.getProperties().put(REFRESH_ACTION, refresh);
    }

    static void refresh(Node root) {
        if (root.getProperties().get(REFRESH_ACTION) instanceof Runnable action) action.run();
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
        return l;
    }

    static Button button(String text, String style, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add(style);
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
        if (action != null) top.getChildren().add(action);
        VBox page = new VBox(24, top);
        page.getStyleClass().add("page");
        return page;
    }

    static TextField input(String prompt) {
        TextField t = new TextField();
        t.setPromptText(prompt);
        t.setMaxWidth(Double.MAX_VALUE);
        return t;
    }

    static <T> TableView<T> table() {
        TableView<T> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
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
                            T value = table.getSelectionModel().getSelectedItem();
                            if (value != null && enabled.test(value)) action.accept(value);
                        });
        b.disableProperty()
                .bind(
                        Bindings.createBooleanBinding(
                                () -> {
                                    T value = table.getSelectionModel().getSelectedItem();
                                    return value == null || !enabled.test(value);
                                },
                                table.getSelectionModel().selectedItemProperty()));
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
                            final Region dot = new Region();
                            final StackPane marker = new StackPane(dot);

                            {
                                dot.getStyleClass().add("selection-dot");
                                marker.getStyleClass().add("selection-marker");
                                marker.setMaxSize(14, 14);
                                marker.setMinSize(14, 14);
                                setAlignment(Pos.CENTER);
                                getStyleClass().add("choice-cell");
                            }

                            @Override
                            protected void updateItem(T item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(empty ? null : marker);
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
        table.getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (o, before, item) -> {
                            note.setText(item == null ? prompt : "Выбрано: " + name.apply(item));
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
