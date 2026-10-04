package ru.library.bootstrap;

import ru.library.config.*;
import ru.library.exception.LibraryException;
import ru.library.model.*;
import ru.library.repository.UserRepository;
import ru.library.security.*;
import java.io.Console;
import java.util.Arrays;
import static ru.library.exception.LibraryException.Code.CONFLICT;

public final class AdminBootstrap {
    private AdminBootstrap() { }
    public static User createFirst(ConnectionFactory factory, String login, String password, String fullName) {
        UserRepository users = new UserRepository();
        PasswordHasher passwords = new PasswordHasher();
        String normalized = Validation.login(login);
        String name = Validation.text(fullName, 200, "ФИО");
        return new Database(factory).transaction(c -> {
            users.lockAdministration(c);
            if (users.count(c) != 0) throw new LibraryException(CONFLICT, "Первичная настройка доступна только до создания пользователей.");
            long id = users.create(c, normalized, passwords.hash(password), name, Role.ADMIN);
            return users.byId(c, id, false).orElseThrow().user();
        });
    }
    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) {
            System.err.println("Запустите первичную настройку из обычного терминала с интерактивной консолью.");
            System.exit(1);
            return;
        }
        String login = console.readLine("Логин администратора: ");
        String name = console.readLine("ФИО администратора: ");
        char[] password = console.readPassword("Пароль: ");
        char[] repeat = console.readPassword("Повторите пароль: ");
        int result = 0;
        try {
            if (password == null || repeat == null || !Arrays.equals(password, repeat))
                throw new IllegalArgumentException("Пароли не совпадают.");
            createFirst(DatabaseConfig.fromEnvironment(), login, new String(password), name);
            console.printf("Администратор создан.%n");
        } catch (LibraryException | IllegalArgumentException exception) {
            console.printf("%s%n", exception.getMessage());
            result = 1;
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (repeat != null) Arrays.fill(repeat, '\0');
        }
        if (result != 0) System.exit(result);
    }
}
