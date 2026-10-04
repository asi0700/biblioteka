package ru.library.repository;

import ru.library.model.*;
import java.sql.*;
import java.util.*;

public final class UserRepository {
    public record Credentials(User user, String hash, long version) {
        @Override public String toString() { return "Credentials[user=" + user.id() + "]"; }
    }
    private static final String SELECT = "SELECT u.*, r.code AS role_code FROM users u JOIN roles r ON r.id=u.role_id ";

    private Credentials map(ResultSet result) throws SQLException {
        User user = new User(result.getLong("id"), result.getString("login"), result.getString("full_name"),
                Role.fromCode(result.getString("role_code")), result.getBoolean("active"));
        return new Credentials(user, result.getString("password_hash"), result.getLong("auth_version"));
    }

    public Optional<Credentials> byLogin(Connection connection, String login) throws SQLException {
        return Sql.one(connection, SELECT + "WHERE u.login=?", this::map, login);
    }

    public Optional<Credentials> byId(Connection connection, long id, boolean lock) throws SQLException {
        return Sql.one(connection, SELECT + (lock ? "WHERE u.id=? FOR UPDATE OF u" : "WHERE u.id=?"), this::map, id);
    }

    public List<User> list(Connection connection) throws SQLException {
        return Sql.list(connection, SELECT + "ORDER BY u.id LIMIT 500", result -> map(result).user());
    }

    public long create(Connection connection, String login, String hash, String fullName, Role role) throws SQLException {
        return Sql.insertId(connection, """
                INSERT INTO users(login,password_hash,full_name,role_id)
                VALUES (?,?,?,(SELECT id FROM roles WHERE code=?)) RETURNING id
                """, login, hash, fullName, role.code());
    }

    public void password(Connection connection, long id, String hash) throws SQLException {
        Sql.update(connection, "UPDATE users SET password_hash=?,auth_version=auth_version+1 WHERE id=?", hash, id);
    }

    public void access(Connection connection, long id, Role role, boolean active) throws SQLException {
        Sql.update(connection, """
                UPDATE users SET role_id=(SELECT id FROM roles WHERE code=?),
                active=?,auth_version=auth_version+1 WHERE id=?
                """, role.code(), active, id);
    }

    public void lockAdministration(Connection connection) throws SQLException {
        Sql.one(connection, "SELECT pg_advisory_xact_lock(73190421)", result -> true);
    }

    public long count(Connection connection) throws SQLException {
        return Sql.one(connection, "SELECT count(*) FROM users", result -> result.getLong(1)).orElseThrow();
    }

    public long activeAdmins(Connection connection) throws SQLException {
        return Sql.one(connection, """
                SELECT count(*) FROM users u JOIN roles r ON r.id=u.role_id WHERE u.active AND r.code='admin'
                """, result -> result.getLong(1)).orElseThrow();
    }

    public List<Role> roles(Connection connection) throws SQLException {
        return Sql.list(connection, "SELECT code FROM roles ORDER BY id", result -> Role.fromCode(result.getString(1)));
    }
}
