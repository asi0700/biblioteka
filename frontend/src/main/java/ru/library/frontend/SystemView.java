package ru.library.frontend;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

final class SystemView {
    final VBox root;

    SystemView(AppContext c) {
        root = Ui.page("Состояние системы", "Подключение к PostgreSQL и сведения о библиотеке.", null);
        Label feedback = Ui.label("Нажмите «Проверить подключение».", "form-note");
        VBox data = new VBox(16);
        Runnable refresh = () -> c.read(root, () -> c.backend.system().status(c.token), status -> {
            feedback.setText("Подключение к PostgreSQL работает.");
            data.getChildren().setAll(
                    Ui.label("База данных: " + status.databaseName(), "row-title"),
                    Ui.label("PostgreSQL: " + status.databaseVersion(), "muted"),
                    Ui.label("Время проверки подключения: " + status.responseMillis() + " мс", "muted"),
                    Ui.label("Таблиц: " + status.tables(), "muted"),
                    Ui.label("Учётных записей: " + status.users() + " · Активных: " + status.activeUsers(), "muted"),
                    Ui.label("Проверено: " + java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                            .withZone(Ui.zone()).format(status.checkedAt()), "muted"));
        }, error -> { data.getChildren().clear(); feedback.setText("Проверка не завершена: " + error); });
        root.getChildren().addAll(Ui.button("Проверить подключение", "primary", refresh), feedback, data);
        Ui.refreshable(root, refresh);
        refresh.run();
    }
}
