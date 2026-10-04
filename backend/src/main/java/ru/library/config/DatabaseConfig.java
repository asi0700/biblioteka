package ru.library.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class DatabaseConfig implements ConnectionFactory {
    private final String url;
    private final String username;
    private final String password;

    public DatabaseConfig(String url, String username, String password) {
        if (url == null || !url.startsWith("jdbc:postgresql:") ||
                username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Задайте URL, пользователя и пароль PostgreSQL.");
        }
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public static DatabaseConfig fromEnvironment() {
        return new DatabaseConfig(System.getenv("LIBRARY_DB_URL"),
                System.getenv("LIBRARY_DB_USER"), System.getenv("LIBRARY_DB_PASSWORD"));
    }

    @Override
    public Connection open() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        properties.setProperty("connectTimeout", "10");
        properties.setProperty("socketTimeout", "30");
        return DriverManager.getConnection(url, properties);
    }
}
