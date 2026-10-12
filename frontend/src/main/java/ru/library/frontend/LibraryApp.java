package ru.library.frontend;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;
import javafx.scene.control.ScrollPane;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Screen;

import ru.library.Backend;
import ru.library.dto.AuthResult;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class LibraryApp extends Application {
    private Stage stage;
    private volatile Backend backend;
    private volatile boolean stopped;
    private AppContext context;
    private Timeline clockTimer;
    private TextField loginField;

    private final ExecutorService loginWorker =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread thread = new Thread(r, "library-login");
                        thread.setDaemon(true);
                        return thread;
                    });

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Библиотека");
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        double width = Math.min(1360, bounds.getWidth() * 0.9);
        double height = Math.min(900, bounds.getHeight() * 0.9);
        stage.setMinWidth(Math.min(1000, width));
        stage.setMinHeight(Math.min(700, height));
        showLogin();
        stage.show();
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setX(bounds.getMinX() + (bounds.getWidth() - width) / 2);
        stage.setY(bounds.getMinY() + (bounds.getHeight() - height) / 2);
    }

    private void showLogin() {
        if (clockTimer != null) clockTimer.stop();
        VBox form = new VBox(16);
        form.setMaxWidth(380);
        form.setMaxHeight(Region.USE_PREF_SIZE);
        form.setAlignment(Pos.TOP_LEFT);
        TextField login = Ui.input("Логин");
        loginField = login;
        login.setId("login");
        PasswordField password = new PasswordField();
        password.setPromptText("Пароль");
        password.setId("password");
        Label feedback = Ui.label("", "error-text");
        feedback.setWrapText(true);
        Label note = Ui.label("Войдите в учётную запись сотрудника.", "muted");
        Button submit = new Button("Войти");
        submit.getStyleClass().add("primary");
        submit.setId("sign-in");
        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setDefaultButton(true);
        form.getChildren()
                .addAll(
                        Ui.label("Библиотека", "login-brand"),
                        Ui.label("С возвращением", "page-title"),
                        note,
                        new VBox(6, Ui.label("Логин", "field-label"), login),
                        new VBox(6, Ui.label("Пароль", "field-label"), password),
                        submit,
                        Ui.button("Регистрация", "secondary", this::showRegistration),
                        feedback);
        submit.setOnAction(
                event -> {
                    String username = login.getText().strip();
                    String secret = password.getText();
                    if (username.isBlank() || secret.isBlank()) {
                        feedback.setText("Введите логин и пароль.");
                        return;
                    }
                    form.setDisable(true);
                    feedback.setText("Входим…");
                    loginWorker.submit(
                            () -> {
                                try {
                                    if (backend == null) backend = Backend.fromEnvironment();
                                    AuthResult result = backend.auth().login(username, secret);
                                    Platform.runLater(
                                            () -> {
                                                if (stopped) {
                                                    backend.auth().logout(result.token());
                                                    return;
                                                }
                                                password.clear();
                                                context =
                                                        new AppContext(
                                                                stage,
                                                                backend,
                                                                result,
                                                                this::showLogin);
                                                showWorkspace(context);
                                            });
                                } catch (Exception ex) {
                                    String message =
                                            ex instanceof IllegalArgumentException
                                                    ? "Подключение к базе не настроено. Задайте"
                                                            + " LIBRARY_DB_URL, LIBRARY_DB_USER и"
                                                            + " LIBRARY_DB_PASSWORD перед запуском."
                                                    : AppContext.message(ex);
                                    Platform.runLater(
                                            () -> {
                                                if (stopped) return;
                                                form.setDisable(false);
                                                feedback.setText(message);
                                                password.clear();
                                                password.requestFocus();
                                            });
                                }
                            });
                });
        StackPane root = new StackPane(form);
        root.getStyleClass().add("login-root");
        ScrollPane scroll = Ui.scrollable(root);
        scroll.getStyleClass().add("login-scroll");
        setScene(scroll);
        Platform.runLater(login::requestFocus);
    }

    private void showRegistration() {
        VBox form = new VBox(16);
        form.setMaxWidth(380);
        form.setMaxHeight(Region.USE_PREF_SIZE);
        TextField name = Ui.input("ФИО"), login = Ui.input("Логин");
        name.setId("register-name"); login.setId("register-login");
        PasswordField password = new PasswordField(), confirmation = new PasswordField();
        password.setId("register-password"); confirmation.setId("register-confirmation");
        Label feedback = Ui.label("", "error-text");
        feedback.setWrapText(true);
        Label note = Ui.label("Создание учётной записи сотрудника. Пароль — от 10 символов. Права администратора назначаются отдельно.", "muted");
        note.setWrapText(true);
        Button submit = Ui.button("Зарегистрироваться", "primary", () -> {});
        submit.setId("register-submit");
        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setDefaultButton(true);
        form.getChildren().addAll(Ui.label("Библиотека", "login-brand"),
                Ui.label("Регистрация", "page-title"), note,
                new VBox(6, Ui.label("ФИО", "field-label"), name),
                new VBox(6, Ui.label("Логин", "field-label"), login),
                new VBox(6, Ui.label("Пароль", "field-label"), password),
                new VBox(6, Ui.label("Повторите пароль", "field-label"), confirmation),
                submit, Ui.button("Вернуться ко входу", "secondary", this::showLogin), feedback);
        submit.setOnAction(event -> {
            String username = login.getText().strip(), fullName = name.getText().strip();
            String secret = password.getText();
            if (username.isBlank() || fullName.isBlank() || secret.isBlank()) {
                feedback.setText("Заполните ФИО, логин и пароль."); return;
            }
            if (!secret.equals(confirmation.getText())) {
                feedback.setText("Пароли не совпадают."); return;
            }
            form.setDisable(true);
            feedback.setText("Создаём учётную запись…");
            loginWorker.submit(() -> {
                try {
                    if (backend == null) backend = Backend.fromEnvironment();
                    backend.auth().register(username, secret, fullName);
                    Platform.runLater(() -> {
                        if (stopped) return;
                        password.clear(); confirmation.clear();
                        showLogin();
                        loginField.setText(username);
                        Ui.information(stage, "Регистрация завершена", "Теперь войдите с вашим логином и паролем. Вам назначена роль сотрудника.");
                    });
                } catch (Exception exception) {
                    Platform.runLater(() -> {
                        if (stopped) return;
                        form.setDisable(false);
                        feedback.setText(AppContext.message(exception));
                    });
                }
            });
        });
        StackPane root = new StackPane(form);
        root.getStyleClass().add("login-root");
        ScrollPane scroll = Ui.scrollable(root);
        scroll.getStyleClass().add("login-scroll");
        setScene(scroll);
    }

    void showWorkspace(AppContext c) {
        setScene(workspace(c));
    }

    BorderPane workspace(AppContext c) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("workspace");
        VBox side = new VBox(8);
        side.getStyleClass().add("sidebar");
        side.setPrefWidth(248);
        VBox brand =
                new VBox(
                        4,
                        Ui.row(Icon.BOOK.createNode(), Ui.label("Библиотека", "brand")),
                        Ui.label("Рабочее место", "muted"));
        brand.getStyleClass().add("brand-box");
        side.getChildren().add(brand);
        VBox content = new VBox(24);
        content.getStyleClass().add("main-content");
        Label breadcrumb = Ui.label("", "muted");
        Label clock = Ui.label("", "muted");
        Runnable updateClock = () -> clock.setText(java.time.ZonedDateTime.now(Ui.zone()).format(
                DateTimeFormatter.ofPattern("d MMMM yyyy · HH:mm:ss", Locale.forLanguageTag("ru"))));
        updateClock.run();
        clockTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateClock.run()));
        clockTimer.setCycleCount(Timeline.INDEFINITE);
        clockTimer.play();
        StackPane page = new StackPane();
        VBox.setVgrow(page, Priority.ALWAYS);
        content.getChildren().add(page);
        content.getChildren()
                .addFirst(
                        Ui.row(
                                breadcrumb,
                                Ui.spacer(),
                                Ui.button(
                                        "Обновить",
                                        "secondary",
                                        () -> {
                                            if (!page.getChildren().isEmpty())
                                                Ui.refresh(page.getChildren().getFirst());
                                        }),
                                clock));
        List<Button> buttons = new ArrayList<>();
        BiConsumer<Section, Supplier<Node>> navigate =
                (section, factory) -> {
                    Button button = new Button(section.title);
                    button.getStyleClass().add("nav-button");
                    button.setMaxWidth(Double.MAX_VALUE);
                    button.setWrapText(true);
                    button.setGraphic(section.icon.createNode());
                    button.setGraphicTextGap(12);
                    button.setOnAction(
                            e -> {
                                buttons.forEach(b -> b.getStyleClass().remove("selected"));
                                button.getStyleClass().add("selected");
                                breadcrumb.setText("Библиотека / " + section.title);
                                Node screen = c.screen(section, factory);
                                if (page.getChildren().isEmpty()
                                        || !(page.getChildren().getFirst() instanceof ScrollPane current)
                                        || current.getContent() != screen)
                                    page.getChildren().setAll(Ui.scrollable(screen));
                            });
                    buttons.add(button);
                    side.getChildren().add(button);
                };
        navigate.accept(Section.CATALOG, () -> new CatalogView(c).root);
        navigate.accept(Section.READERS, () -> new ReadersView(c).root);
        navigate.accept(Section.CIRCULATION, () -> new CirculationView(c, false).root);
        navigate.accept(Section.FINES, () -> new CirculationView(c, true).root);
        Separator separator = new Separator();
        VBox.setMargin(separator, new Insets(20, 0, 10, 0));
        side.getChildren().add(separator);
        side.getChildren().add(Ui.label("УПРАВЛЕНИЕ", "nav-caption"));
        navigate.accept(Section.DICTIONARIES, () -> AdministrationView.dictionaries(c));
        if (c.admin()) navigate.accept(Section.USERS, () -> AdministrationView.users(c));
        navigate.accept(Section.SETTINGS, () -> AdministrationView.settings(c));
        if (c.admin()) navigate.accept(Section.SYSTEM, () -> new SystemView(c).root);
        Label accountName = Ui.label(c.user.fullName(), "profile-name");
        accountName.setMinWidth(0);
        accountName.setMaxWidth(Double.MAX_VALUE);
        accountName.setTooltip(new Tooltip(c.user.fullName() + " · " + c.user.login()));
        HBox.setHgrow(accountName, Priority.ALWAYS);
        Label role = Ui.label(c.admin() ? "Админ" : "Сотрудник", "profile-role");
        role.setMinWidth(Region.USE_PREF_SIZE);
        role.setTooltip(new Tooltip(c.admin() ? "Роль: администратор" : "Роль: сотрудник"));
        HBox account = Ui.row(accountName, role);
        account.setSpacing(8);
        account.getStyleClass().add("profile-row");
        Button logout = Ui.button("Выйти", "logout", c::logout);
        logout.setMaxWidth(Double.MAX_VALUE);
        side.getChildren().addAll(Ui.spacer(), new Separator(), account, logout);
        ScrollPane sideScroll = Ui.scrollable(side);
        sideScroll.getStyleClass().add("sidebar-scroll");
        sideScroll.setPrefWidth(248);
        sideScroll.setMinWidth(248);
        root.setLeft(sideScroll);
        root.setCenter(content);
        root.widthProperty()
                .addListener(
                        (o, old, width) -> {
                            boolean compact = width.doubleValue() < 1180;
                            side.setPrefWidth(248);
                            sideScroll.setPrefWidth(248);
                            content.setStyle(compact ? "-fx-padding: 24 20 20 20;" : "");
                        });
        buttons.getFirst().fire();
        return root;
    }

    private void setScene(Parent root) {
        if (stage.getScene() == null) {
            Scene scene = new Scene(root);
            scene.getStylesheets().add(Ui.stylesheet());
            stage.setScene(scene);
        } else stage.getScene().setRoot(root);
    }

    @Override
    public void stop() {
        stopped = true;
        if (clockTimer != null) clockTimer.stop();
        if (context != null) context.close();
        loginWorker.shutdownNow();
    }
}
