package ru.library.repository;

import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;

public final class Sql {
    private Sql() { }
    @FunctionalInterface
    public interface Mapper<T> { T map(ResultSet result) throws SQLException; }

    public static <T> List<T> list(Connection connection, String sql, Mapper<T> mapper, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (result.next()) rows.add(mapper.map(result));
                return List.copyOf(rows);
            }
        }
    }

    public static <T> Optional<T> one(Connection connection, String sql, Mapper<T> mapper, Object... parameters) throws SQLException {
        List<T> rows = list(connection, sql, mapper, parameters);
        if (rows.size() > 1) throw new SQLException("Ожидалась одна строка.");
        return rows.stream().findFirst();
    }

    public static int update(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            return statement.executeUpdate();
        }
    }

    public static long insertId(Connection connection, String sql, Object... parameters) throws SQLException {
        return one(connection, sql, result -> result.getLong(1), parameters).orElseThrow(() -> new SQLException("Запись не создана."));
    }

    private static void bind(PreparedStatement statement, Object[] parameters) throws SQLException {
        statement.setQueryTimeout(30);
        for (int index = 0; index < parameters.length; index++) statement.setObject(index + 1, parameters[index]);
    }

    static Long nullableLong(ResultSet result, String name) throws SQLException {
        return result.getObject(name, Long.class);
    }

    static java.time.Instant instant(ResultSet result, String name) throws SQLException {
        OffsetDateTime value = result.getObject(name, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
