package ru.library.frontend;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import ru.library.dto.DictionaryEntry;
import ru.library.dto.LoanSettings;
import ru.library.model.DictionaryKind;
import ru.library.model.Role;
import ru.library.model.User;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

final class AdministrationView {
    private AdministrationView() {}

    static VBox dictionaries(AppContext c) {
        VBox root =
                Ui.page(
                        Section.DICTIONARIES.title,
                        "Авторы, жанры и издательства для каталога книг.",
                        null);
        ComboBox<DictionaryKind> kind =
                Ui.combo(
                        List.of(DictionaryKind.values()),
                        value ->
                                switch (value) {
                                    case AUTHOR -> "Авторы";
                                    case GENRE -> "Жанры";
                                    case PUBLISHER -> "Издательства";
                                });
        kind.setValue(DictionaryKind.AUTHOR);
        kind.setPrefWidth(240);
        TextField search = Ui.input("Поиск по справочнику");
        HBox.setHgrow(search, Priority.ALWAYS);
        TableView<DictionaryEntry> table = Ui.table();
        Ui.column(table, "Название", 650, DictionaryEntry::name);
        List<DictionaryEntry> entries = new ArrayList<>();
        Runnable filter =
                () ->
                        table.getItems()
                                .setAll(
                                        entries.stream()
                                                .filter(
                                                        e ->
                                                                e.name()
                                                                        .toLowerCase(Locale.ROOT)
                                                                        .contains(
                                                                                search.getText()
                                                                                        .strip()
                                                                                        .toLowerCase(
                                                                                                Locale
                                                                                                        .ROOT)))
                                                .toList());
        Runnable reload =
                () -> {
                    DictionaryKind selected = kind.getValue();
                    table.setDisable(true);
                    c.read(
                            root,
                            () -> c.backend.dictionaries().list(c.token, selected),
                            values -> {
                                entries.clear();
                                entries.addAll(values);
                                filter.run();
                                table.setDisable(false);
                            },
                            error -> {
                                table.setDisable(false);
                                Ui.error(c.stage, error);
                            });
                };
        search.textProperty().addListener((o, a, b) -> filter.run());
        kind.setOnAction(
                e -> {
                    if (table.getSelectionModel() != null)
                        table.getSelectionModel().clearSelection();
                    reload.run();
                });
        HBox toolbar = Ui.row(kind, search);
        HBox actions = Ui.row(Ui.label("До 500 записей в справочнике", "muted"), Ui.spacer());
        actions.getStyleClass().add("action-bar");
        if (c.admin()) {
            actions.getChildren()
                    .set(
                            0,
                            Ui.selectionNote(
                                    table, "Выберите запись для действий", DictionaryEntry::name));
            toolbar.getChildren()
                    .add(
                            Ui.button(
                                    "Добавить",
                                    "primary",
                                    () -> dictionaryForm(c, kind.getValue(), null, reload)));
            actions.getChildren()
                    .addAll(
                            Ui.selectedButton(
                                    "Изменить",
                                    "secondary",
                                    table,
                                    entry -> dictionaryForm(c, kind.getValue(), entry, reload)),
                            Ui.selectedButton(
                                    "Удалить",
                                    "danger",
                                    table,
                                    entry -> {
                                        DictionaryKind selected = kind.getValue();
                                        Ui.confirm(
                                                c,
                                                "Удалить запись?",
                                                entry.name()
                                                        + "\n"
                                                        + "Запись, используемую в каталоге, удалить"
                                                        + " нельзя.",
                                                () ->
                                                        c.backend
                                                                .dictionaries()
                                                                .delete(
                                                                        c.token,
                                                                        selected,
                                                                        entry.id()),
                                                reload);
                                    }));
        } else Ui.readOnly(table);
        root.getChildren().addAll(toolbar, table, actions);
        Ui.refreshable(root, reload);
        reload.run();
        return root;
    }

    private static void dictionaryForm(
            AppContext c, DictionaryKind kind, DictionaryEntry entry, Runnable done) {
        Ui.Form form =
                new Ui.Form(c, entry == null ? "Новая запись" : "Изменить запись", "Сохранить");
        TextField name = form.text("Название *", entry == null ? "" : entry.name());
        form.save(
                () -> {
                    String value = Ui.required(name.getText().strip(), "Название");
                    return () -> {
                        if (entry == null) c.backend.dictionaries().create(c.token, kind, value);
                        else c.backend.dictionaries().update(c.token, kind, entry.id(), value);
                    };
                },
                done);
        form.show();
    }

    static VBox users(AppContext c) {
        VBox root =
                Ui.page(Section.USERS.title, "Учётные записи и права доступа к библиотеке.", null);
        TableView<User> table = Ui.table();
        Ui.titleColumn(table, "Сотрудник", 350, User::fullName, User::login);
        Ui.column(table, "Роль", 220, user -> roleName(user.role()));
        Ui.column(table, "Статус", 160, user -> user.active() ? "Активен" : "Отключён");
        Runnable reload =
                () ->
                        c.read(
                                root,
                                () -> c.backend.users().list(c.token),
                                records -> table.getItems().setAll(records));
        root.getChildren()
                .add(
                        Ui.row(
                                Ui.label("До 500 сотрудников", "muted"),
                                Ui.spacer(),
                                Ui.button(
                                        "Добавить сотрудника",
                                        "primary",
                                        () -> createUser(c, reload))));
        HBox actions =
                Ui.row(
                        Ui.selectionNote(table, "Выберите сотрудника для действий", User::fullName),
                        Ui.spacer(),
                        Ui.selectedButton(
                                "Сбросить пароль",
                                "secondary",
                                table,
                                user -> {
                                    Ui.Form form =
                                            new Ui.Form(
                                                    c,
                                                    "Новый пароль сотрудника",
                                                    "Изменить пароль");
                                    form.body
                                            .getChildren()
                                            .add(Ui.label(user.fullName(), "row-title"));
                                    PasswordField password =
                                            form.field("Новый пароль *", new PasswordField());
                                    PasswordField confirmation =
                                            form.field("Повторите пароль *", new PasswordField());
                                    form.save(
                                            () -> {
                                                String value =
                                                        matchingPassword(password, confirmation);
                                                return () ->
                                                        c.backend
                                                                .users()
                                                                .resetPassword(
                                                                        c.token, user.id(), value);
                                            },
                                            () -> {
                                                if (user.id() == c.user.id()) {
                                                    c.close();
                                                    c.signedOut.run();
                                                } else reload.run();
                                            });
                                    form.show();
                                }),
                        Ui.selectedButton(
                                "Настроить доступ",
                                "primary",
                                table,
                                user -> {
                                    Ui.Form form = new Ui.Form(c, "Доступ сотрудника", "Сохранить");
                                    form.body
                                            .getChildren()
                                            .add(Ui.label(user.fullName(), "row-title"));
                                    ComboBox<Role> role =
                                            form.field(
                                                    "Роль",
                                                    Ui.combo(
                                                            List.of(),
                                                            AdministrationView::roleName));
                                    CheckBox active =
                                            form.field(
                                                    "Статус",
                                                    new CheckBox("Учётная запись активна"));
                                    active.setSelected(user.active());
                                    form.body
                                            .getChildren()
                                            .add(
                                                    Ui.label(
                                                            "Изменение доступа завершит текущие"
                                                                    + " сеансы сотрудника.",
                                                            "form-note"));
                                    form.save(
                                            () -> {
                                                Role selected =
                                                        Ui.required(role.getValue(), "Роль");
                                                boolean enabled = active.isSelected();
                                                return () ->
                                                        c.backend
                                                                .users()
                                                                .setAccess(
                                                                        c.token, user.id(),
                                                                        selected, enabled);
                                            },
                                            () -> {
                                                if (user.id() == c.user.id()) {
                                                    c.close();
                                                    c.signedOut.run();
                                                } else reload.run();
                                            });
                                    form.show();
                                    loadRoles(c, form, role, user.role());
                                }));
        actions.getStyleClass().add("action-bar");
        root.getChildren().addAll(table, actions);
        Ui.refreshable(root, reload);
        reload.run();
        return root;
    }

    private static void createUser(AppContext c, Runnable done) {
        Ui.Form form = new Ui.Form(c, "Добавить сотрудника", "Создать учётную запись");
        TextField name = form.text("ФИО *", ""), login = form.text("Логин *", "");
        PasswordField password = form.field("Пароль *", new PasswordField());
        PasswordField confirmation = form.field("Повторите пароль *", new PasswordField());
        ComboBox<Role> role = form.field("Роль", Ui.combo(List.of(), AdministrationView::roleName));
        form.save(
                () -> {
                    String fullName = Ui.required(name.getText().strip(), "ФИО"),
                            username = Ui.required(login.getText().strip(), "Логин");
                    String secret = matchingPassword(password, confirmation);
                    Role selected = Ui.required(role.getValue(), "Роль");
                    return () ->
                            c.backend.users().create(c.token, username, secret, fullName, selected);
                },
                done);
        form.show();
        loadRoles(c, form, role, Role.USER);
    }

    private static void loadRoles(AppContext c, Ui.Form form, ComboBox<Role> role, Role selected) {
        c.execute(
                form.getDialogPane(),
                () -> c.backend.users().roles(c.token),
                values -> {
                    role.getItems().setAll(values);
                    role.setValue(values.contains(selected) ? selected : null);
                },
                form::problem);
    }

    static VBox settings(AppContext c) {
        VBox root =
                Ui.page(
                        Section.SETTINGS.title,
                        "Правила выдачи и безопасность вашей учётной записи.",
                        null);
        TextField days = Ui.input("Количество дней"), rate = Ui.input("Рублей в день");
        record SettingsInput(String days, String rate) {}
        ObjectProperty<SettingsInput> saved = new SimpleObjectProperty<>(new SettingsInput("", ""));
        Supplier<SettingsInput> input = () -> new SettingsInput(days.getText(), rate.getText());
        days.setMaxWidth(280);
        rate.setMaxWidth(280);
        Label feedback = Ui.label("", "muted");
        VBox policy =
                new VBox(
                        16,
                        Ui.label("Правила выдачи", "form-title"),
                        new VBox(6, Ui.label("Срок выдачи, дней", "field-label"), days),
                        new VBox(6, Ui.label("Штраф за день просрочки, ₽", "field-label"), rate),
                        Ui.label(
                                "Изменения применяются к новым выдачам. Ставка уже выданных книг"
                                        + " сохраняется.",
                                "form-note"));
        policy.getStyleClass().add("settings-section");
        if (c.admin()) {
            Button save =
                    Ui.button(
                            "Сохранить правила",
                            "primary",
                            () -> {
                                try {
                                    int count = Integer.parseInt(days.getText().strip());
                                    BigDecimal amount =
                                            new BigDecimal(
                                                    rate.getText().strip().replace(',', '.'));
                                    LoanSettings value = new LoanSettings(count, amount);
                                    c.execute(
                                            policy,
                                            () -> {
                                                c.backend.settings().update(c.token, value);
                                                return null;
                                            },
                                            ignored -> {
                                                c.dataChanged();
                                                feedback.setText("Правила сохранены");
                                                saved.set(input.get());
                                            },
                                            feedback::setText);
                                } catch (IllegalArgumentException e) {
                                    feedback.setText("Укажите целое число дней и сумму в рублях.");
                                }
                            });
            policy.getChildren().add(save);
        } else {
            days.setEditable(false);
            rate.setEditable(false);
            policy.getChildren().add(Ui.label("Правила изменяет администратор.", "muted"));
        }
        policy.getChildren().add(feedback);
        VBox security =
                new VBox(
                        14,
                        Ui.label("Безопасность", "form-title"),
                        Ui.label(c.user.fullName() + " · " + c.user.login(), "muted"),
                        Ui.button("Изменить мой пароль", "secondary", () -> changePassword(c)));
        security.getStyleClass().add("settings-section");
        VBox sections = new VBox(24, policy, security);
        ScrollPane scroll = new ScrollPane(sections);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().add(scroll);
        Runnable reload =
                () -> {
                    if (!input.get().equals(saved.get())) return;
                    policy.setDisable(true);
                    c.read(
                            root,
                            () -> c.backend.settings().get(c.token),
                            settings -> {
                                SettingsInput values =
                                        new SettingsInput(
                                                Integer.toString(settings.loanDays()),
                                                settings.dailyFineRate().toPlainString());
                                saved.set(values);
                                days.setText(values.days());
                                rate.setText(values.rate());
                                policy.setDisable(false);
                            },
                            error -> {
                                policy.setDisable(false);
                                feedback.setText(error);
                            });
                };
        Ui.refreshable(root, reload);
        reload.run();
        return root;
    }

    private static void changePassword(AppContext c) {
        Ui.Form form = new Ui.Form(c, "Изменить пароль", "Изменить и выйти");
        PasswordField current = form.field("Текущий пароль *", new PasswordField());
        PasswordField password = form.field("Новый пароль *", new PasswordField());
        PasswordField confirmation = form.field("Повторите новый пароль *", new PasswordField());
        form.body
                .getChildren()
                .add(Ui.label("После изменения войдите с новым паролем.", "form-note"));
        form.save(
                () -> {
                    String old = Ui.required(current.getText(), "Текущий пароль"),
                            value = matchingPassword(password, confirmation);
                    return () -> c.backend.users().changeOwnPassword(c.token, old, value);
                },
                () -> {
                    c.close();
                    c.signedOut.run();
                });
        form.show();
    }

    private static String matchingPassword(PasswordField password, PasswordField confirmation) {
        String value = Ui.required(password.getText(), "Пароль");
        if (!value.equals(confirmation.getText()))
            throw new IllegalArgumentException("Пароли не совпадают.");
        return value;
    }

    private static String roleName(Role role) {
        return role == Role.ADMIN ? "Администратор" : "Сотрудник";
    }
}
