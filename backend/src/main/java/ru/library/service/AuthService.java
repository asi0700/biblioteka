package ru.library.service;

import ru.library.config.Database;
import ru.library.dto.*;
import ru.library.exception.LibraryException;
import ru.library.model.*;
import ru.library.repository.UserRepository;
import ru.library.security.*;
import java.sql.*;
import java.time.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static ru.library.exception.LibraryException.Code.*;

public final class AuthService {
    private record Session(long userId, long version, Instant expiresAt) { }
    private final Database database;
    private final UserRepository users;
    private final PasswordHasher passwords;
    private final Clock clock;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public AuthService(Database database, UserRepository users, PasswordHasher passwords, Clock clock) {
        this.database = database; this.users = users; this.passwords = passwords; this.clock = clock;
        dummyHash = passwords.hash(randomToken());
    }

    public AuthResult login(String login, String password) {
        final String normalized;
        try { normalized = Validation.login(login); }
        catch (LibraryException exception) { throw denied(); }
        return database.transaction(connection -> {
            var credentials = users.byLogin(connection, normalized);
            boolean verified = passwords.verify(password, credentials.map(UserRepository.Credentials::hash).orElse(dummyHash));
            if (credentials.isEmpty() || !verified || !credentials.get().user().active()) throw denied();
            SessionToken token = new SessionToken(randomToken());
            var current = credentials.get();
            Instant now = clock.instant();
            sessions.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
            sessions.put(token.value(), new Session(current.user().id(), current.version(), now.plus(Duration.ofHours(8))));
            return new AuthResult(token, current.user());
        });
    }

    public void logout(SessionToken token) {
        if (token != null && token.value() != null) sessions.remove(token.value());
    }
    public User currentUser(SessionToken token) {
        return database.transaction(connection -> require(connection, token, false));
    }

    // Роль и версия авторизации проверяются в БД при каждом обращении к сервисам.
    public User require(Connection connection, SessionToken token, boolean admin) throws SQLException {
        if (token == null || token.value() == null) throw expired();
        Session session = sessions.get(token.value());
        if (session == null || !clock.instant().isBefore(session.expiresAt())) {
            logout(token);
            throw expired();
        }
        var credentials = users.byId(connection, session.userId(), true).orElseThrow(AuthService::expired);
        if (!credentials.user().active() || credentials.version() != session.version()) {
            logout(token);
            throw expired();
        }
        if (admin && credentials.user().role() != Role.ADMIN)
            throw new LibraryException(FORBIDDEN, "Для этой операции нужны права администратора.");
        return credentials.user();
    }
    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
    private static LibraryException denied() { return new LibraryException(UNAUTHENTICATED, "Неверный логин или пароль."); }
    private static LibraryException expired() { return new LibraryException(UNAUTHENTICATED, "Сеанс недействителен. Войдите заново."); }
}
