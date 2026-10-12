package ru.library.bootstrap;

import ru.library.config.ConnectionFactory;
import ru.library.config.DatabaseConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

public final class DemoDataLoader {
    private DemoDataLoader() { }

    public static void main(String[] args) {
        try {
            load(DatabaseConfig.fromEnvironment());
            System.out.println("Демонстрационные данные загружены. Нажмите «Обновить» в приложении.");
        } catch (SQLException exception) {
            System.err.println("Загрузка не завершена. Проверьте подключение, наличие таблиц и активного администратора. Код: " + exception.getSQLState());
            System.exit(1);
        } catch (IOException | IllegalArgumentException exception) {
            System.err.println("Проверьте настройки подключения и наличие demo-data.sql в сборке backend.");
            System.exit(1);
        }
    }

    public static void load(ConnectionFactory connections) throws SQLException, IOException {
        String sql;
        try (var input = DemoDataLoader.class.getResourceAsStream("/db/demo-data.sql")) {
            if (input == null) throw new IOException("Missing demo-data.sql");
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (var connection = connections.open(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
