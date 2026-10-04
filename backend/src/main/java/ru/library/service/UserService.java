package ru.library.service;

import ru.library.config.Database;
import ru.library.dto.SessionToken;
import ru.library.exception.LibraryException;
import ru.library.model.*;
import ru.library.repository.UserRepository;
import ru.library.security.*;
import java.util.List;
import static ru.library.exception.LibraryException.Code.*;

public final class UserService {
    private final Database database;
    private final AuthService auth;
    private final UserRepository users;
    private final PasswordHasher passwords;

    public UserService(Database database, AuthService auth, UserRepository users, PasswordHasher passwords) {
        this.database = database; this.auth = auth; this.users = users; this.passwords = passwords;
    }
    public List<User> list(SessionToken token) {
        return database.transaction(c -> { auth.require(c, token, true); return users.list(c); });
    }
    public List<Role> roles(SessionToken token) {
        return database.transaction(c -> { auth.require(c, token, true); return users.roles(c); });
    }
    public User create(SessionToken token, String login, String password, String fullName, Role role) {
        String name = Validation.text(fullName, 200, "ФИО");
        String normalized = Validation.login(login);
        Validation.required(role, "роль");
        return database.transaction(c -> {
            users.lockAdministration(c);
            auth.require(c, token, true);
            long id = users.create(c, normalized, passwords.hash(password), name, role);
            return users.byId(c, id, false).orElseThrow().user();
        });
    }
    public void resetPassword(SessionToken token, long userId, String newPassword) {
        Validation.id(userId);
        database.transaction(c -> {
            users.lockAdministration(c);
            auth.require(c, token, true);
            users.byId(c, userId, true).orElseThrow(() -> new LibraryException(NOT_FOUND, "Пользователь не найден."));
            users.password(c, userId, passwords.hash(newPassword));
            return null;
        });
    }
    public void changeOwnPassword(SessionToken token, String currentPassword, String newPassword) {
        database.transaction(c -> {
            users.lockAdministration(c);
            User user = auth.require(c, token, false);
            var credentials = users.byId(c, user.id(), true).orElseThrow();
            if (!passwords.verify(currentPassword, credentials.hash()))
                throw new LibraryException(UNAUTHENTICATED, "Неверный текущий пароль.");
            users.password(c, user.id(), passwords.hash(newPassword));
            return null;
        });
    }
    public void setAccess(SessionToken token, long userId, Role role, boolean active) {
        Validation.id(userId);
        Validation.required(role, "роль");
        database.transaction(c -> {
            users.lockAdministration(c);
            auth.require(c, token, true);
            User target = users.byId(c, userId, true).orElseThrow(() -> new LibraryException(NOT_FOUND, "Пользователь не найден.")).user();
            if (target.active() && target.role() == Role.ADMIN && (!active || role != Role.ADMIN) && users.activeAdmins(c) <= 1)
                throw new LibraryException(CONFLICT, "Нельзя отключить или понизить последнего администратора.");
            users.access(c, userId, role, active);
            return null;
        });
    }
}
