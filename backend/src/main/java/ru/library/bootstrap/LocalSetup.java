package ru.library.bootstrap;

import ru.library.config.DatabaseConfig;
import ru.library.exception.LibraryException;

import java.io.Console;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

public final class LocalSetup {
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]{0,62}");
    private static final Pattern LOCAL_URL = Pattern.compile("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):([0-9]+)/([a-z_][a-z0-9_]{0,62})");
    static final Set<String> TABLES = Set.of("roles", "users", "authors", "genres", "publishers", "books",
            "book_authors", "book_copies", "readers", "reader_tickets", "loan_statuses", "loans", "fines", "settings");
    static final Set<String> KEYS = Set.of("LIBRARY_DB_URL", "LIBRARY_DB_USER", "LIBRARY_DB_PASSWORD", "LIBRARY_ZONE");
    private LocalSetup() { }

    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) {
            System.err.println("Откройте терминал в папке проекта и выполните setup-db.cmd.");
            System.exit(1); return;
        }
        char[] adminPassword = null;
        try {
            Path envFile = Path.of(args.length == 0 ? ".env" : args[0]).toAbsolutePath();
            Map<String,String> saved = readEnv(envFile);
            Integer requestedPort = args.length > 1 ? Integer.valueOf(args[1]) : null;
            Config config = configuration(saved, requestedPort);
            console.printf("\nНастройка библиотеки · PostgreSQL %s:%d\n", config.host(), config.port());
            console.printf("Будет использована база %s и пользователь %s. Существующие данные сохраняются.\n", config.database(), config.username());
            String administrator = console.readLine("Пользователь PostgreSQL [postgres]: ");
            if (administrator == null) throw new IllegalArgumentException("Настройка отменена.");
            administrator = administrator.isBlank() ? "postgres" : administrator.strip();
            if (administrator.equals(config.username())) throw new IllegalArgumentException("Для приложения нужна отдельная учётная запись, отличная от администратора PostgreSQL.");
            adminPassword = console.readPassword("Пароль PostgreSQL, указанный при установке: ");
            if (adminPassword == null) throw new IllegalArgumentException("Настройка отменена.");
            try (Connection admin = connect(config, "postgres", administrator, new String(adminPassword))) {
                boolean roleExists = exists(admin, "SELECT 1 FROM pg_roles WHERE rolname=?", config.username());
                String owner = databaseOwner(admin, config.database());
                if (owner != null && !owner.equals(config.username()))
                    throw new IllegalArgumentException("База " + config.database() + " уже принадлежит другому пользователю. Автоматическая настройка её не изменяет. Используйте ручное подключение по README.");
                if (roleExists) {
                    if (saved.getOrDefault("LIBRARY_DB_PASSWORD", "").isBlank())
                        throw new IllegalArgumentException("Пользователь " + config.username() + " уже существует, но его пароль не сохранён в .env. Укажите существующий пароль вручную; настройка его не сбрасывает.");
                    try (Connection ignored = connect(config, "postgres", config.username(), config.password())) { }
                    if (privilegedRole(admin, config.username()))
                        throw new IllegalArgumentException("Пользователь приложения имеет административные права PostgreSQL. Выберите отдельную обычную учётную запись в .env.");
                }
                writeEnv(envFile, config);
                if (!roleExists) {
                    try (Statement statement = admin.createStatement()) {
                        statement.execute("CREATE ROLE " + identifier(config.username()) + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD " + literal(config.password()));
                    }
                    console.printf("Пользователь приложения создан.\n");
                }
                if (owner == null) {
                    try (Statement statement = admin.createStatement()) {
                        statement.execute("CREATE DATABASE " + identifier(config.database()) + " OWNER " + identifier(config.username()) + " ENCODING 'UTF8' TEMPLATE template0");
                    }
                    console.printf("База создана.\n");
                }
            } finally { Arrays.fill(adminPassword, '\0'); }

            DatabaseConfig database = new DatabaseConfig(config.url(), config.username(), config.password());
            try (Connection connection = database.open()) {
                Set<String> tables = new HashSet<>();
                try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname='public'")) {
                    while (result.next()) tables.add(result.getString(1));
                }
                verifyTables(tables);
                try (Statement statement = connection.createStatement()) {
                    if (tables.isEmpty()) { statement.execute(resource("/db/schema.sql")); console.printf("Созданы таблицы библиотеки.\n"); }
                    statement.execute(resource("/db/reference-data.sql"));
                }
                if (!exists(connection, "SELECT 1 FROM public.users LIMIT 1", null)) createAdministrator(console, database);
                else console.printf("Учётные записи уже существуют — повторное создание администратора не требуется.\n");
            }
            console.printf("\nГотово. Настройки сохранены в .env. Для запуска откройте start.cmd.\n");
        } catch (SQLException exception) {
            String state = exception.getSQLState();
            if (state != null && state.startsWith("28")) System.err.println("Не удалось войти в PostgreSQL. Проверьте пароль администратора или сохранённые реквизиты .env.");
            else if (state != null && state.startsWith("08")) System.err.println("PostgreSQL недоступен. Завершите установку и убедитесь, что служба запущена и порт указан верно.");
            else System.err.println("Настройка базы не завершена. Проверьте права пользователя PostgreSQL и структуру существующей базы. Код: " + state);
            System.exit(1);
        } catch (IOException exception) {
            System.err.println("Не удалось прочитать или сохранить файлы настройки. Проверьте доступ к папке проекта."); System.exit(1);
        } catch (IllegalArgumentException | LibraryException exception) {
            System.err.println(exception.getMessage()); System.exit(1);
        } finally { if (adminPassword != null) Arrays.fill(adminPassword, '\0'); }
    }

    static Config configuration(Map<String,String> saved, Integer requestedPort) {
        int port = requestedPort == null ? 5432 : requestedPort;
        String url = saved.getOrDefault("LIBRARY_DB_URL", "");
        if (url.isBlank()) url = "jdbc:postgresql://localhost:" + port + "/library";
        var match = LOCAL_URL.matcher(url);
        if (!match.matches()) throw new IllegalArgumentException("Автонастройка работает только с локальным PostgreSQL и URL вида jdbc:postgresql://localhost:5432/library. Существующий .env не изменён.");
        port = Integer.parseInt(match.group(2));
        if (port < 1 || port > 65535 || requestedPort != null && requestedPort != port)
            throw new IllegalArgumentException("Проверьте порт: параметр запуска и .env должны совпадать (1–65535).");
        String username = saved.getOrDefault("LIBRARY_DB_USER", "");
        if (username.isBlank()) username = "library_app";
        identifier(username);
        if (username.equals("postgres") || username.startsWith("pg_") || Set.of("postgres", "template0", "template1").contains(match.group(3)))
            throw new IllegalArgumentException("Для приложения нужны отдельные база и пользователь, не системные объекты PostgreSQL.");
        String password = saved.getOrDefault("LIBRARY_DB_PASSWORD", "");
        if (password.isBlank()) { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); password = Base64.getEncoder().encodeToString(bytes); }
        String zone = saved.getOrDefault("LIBRARY_ZONE", "Europe/Moscow");
        if (zone.isBlank()) zone = "Europe/Moscow";
        java.time.ZoneId.of(zone);
        return new Config(url, match.group(1), port, match.group(3), username, password, zone);
    }
    static Map<String,String> readEnv(Path file) throws IOException {
        Map<String,String> values = new LinkedHashMap<>();
        if (!Files.exists(file)) return values;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            line = line.replace("\uFEFF", "").strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int separator = line.indexOf('=');
            if (separator < 1) throw new IllegalArgumentException("Некорректная строка в .env: ожидается ИМЯ=значение.");
            String key = line.substring(0, separator).strip(), value = line.substring(separator + 1).strip();
            if (!KEYS.contains(key)) continue;
            if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) value = value.substring(1, value.length() - 1);
            values.put(key, value);
        }
        return values;
    }
    static void writeEnv(Path file, Config config) throws IOException {
        Map<String,String> values = new LinkedHashMap<>();
        values.put("LIBRARY_DB_URL", config.url()); values.put("LIBRARY_DB_USER", config.username());
        values.put("LIBRARY_DB_PASSWORD", config.password()); values.put("LIBRARY_ZONE", config.zone());
        List<String> lines = Files.exists(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : new ArrayList<>();
        List<String> output = new ArrayList<>();
        for (String line : lines) {
            int separator = line.indexOf('='); String key = separator < 0 ? "" : line.substring(0, separator).replace("\uFEFF", "").strip();
            if (!KEYS.contains(key)) output.add(line);
        }
        values.forEach((key,value) -> output.add(key + "=\"" + value + "\""));
        Path parent = file.toAbsolutePath().getParent(); Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".env.", ".tmp");
        try {
            Files.write(temporary, output, StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    static void verifyTables(Set<String> tables) {
        if (!tables.isEmpty() && !tables.equals(TABLES))
            throw new IllegalArgumentException("В базе обнаружена другая или неполная схема. Таблицы не удалены. Проверьте базу вручную; повторный запуск не выполняет миграции.");
    }
    static String identifier(String value) {
        if (!IDENTIFIER.matcher(value).matches()) throw new IllegalArgumentException("Имя базы или пользователя: латинские строчные буквы, цифры и _, до 63 символов; первая буква или _.");
        return "\"" + value + "\"";
    }
    private static String literal(String value) { return "'" + value.replace("'", "''") + "'"; }
    private static Connection connect(Config config, String database, String user, String password) throws SQLException {
        Properties properties = new Properties(); properties.setProperty("user", user); properties.setProperty("password", password);
        properties.setProperty("connectTimeout", "5"); properties.setProperty("socketTimeout", "30");
        properties.setProperty("options", "-c standard_conforming_strings=on");
        return DriverManager.getConnection("jdbc:postgresql://" + config.host() + ":" + config.port() + "/" + database, properties);
    }
    private static boolean exists(Connection c, String sql, String argument) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            if (argument != null) statement.setString(1, argument);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }
    private static String databaseOwner(Connection c, String name) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement("SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname=?")) {
            statement.setString(1, name); try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getString(1) : null; }
        }
    }
    private static boolean privilegedRole(Connection c, String name) throws SQLException {
        return exists(c, "SELECT 1 FROM pg_roles WHERE rolname=? AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)", name);
    }
    private static String resource(String name) throws IOException {
        try (var input = LocalSetup.class.getResourceAsStream(name)) {
            if (input == null) throw new IOException("Missing SQL resource"); return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    private static void createAdministrator(Console console, DatabaseConfig database) {
        console.printf("\nТеперь создайте учётную запись для входа в приложение (отдельно от PostgreSQL).\n");
        while (true) {
            String login = console.readLine("Логин администратора библиотеки: ");
            String name = console.readLine("ФИО: ");
            if (login == null || name == null) throw new IllegalArgumentException("Создание администратора отменено. Повторите setup-db.cmd, база уже подготовлена.");
            char[] password = console.readPassword("Пароль (от 10 символов): ");
            char[] repeat = console.readPassword("Повторите пароль: ");
            try {
                if (password == null || repeat == null) throw new IllegalArgumentException("Создание администратора отменено.");
                if (!Arrays.equals(password, repeat)) { console.printf("Пароли не совпали. Попробуйте снова.\n"); continue; }
                AdminBootstrap.createFirst(database, login, new String(password), name); console.printf("Администратор создан.\n"); return;
            } catch (LibraryException exception) {
                if (exception.code() != LibraryException.Code.VALIDATION) throw exception;
                console.printf("%s\n", exception.getMessage());
            } finally { if (password != null) Arrays.fill(password, '\0'); if (repeat != null) Arrays.fill(repeat, '\0'); }
        }
    }
    record Config(String url, String host, int port, String database, String username, String password, String zone) {
        @Override public String toString() { return "LocalSetup.Config[credentials hidden]"; }
    }
}
