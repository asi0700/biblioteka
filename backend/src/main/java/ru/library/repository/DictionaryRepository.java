package ru.library.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public final class DictionaryRepository {
    public enum Kind {
        AUTHOR("authors", 200), GENRE("genres", 100), PUBLISHER("publishers", 200);
        private final String table;
        private final int maxLength;
        Kind(String table, int maxLength) { this.table = table; this.maxLength = maxLength; }
        public int maxLength() { return maxLength; }
    }
    public record Entry(long id, String name) { }

    // Название таблицы выбирается только из закрытого списка справочников.
    public List<Entry> list(Connection connection, Kind kind) throws SQLException {
        return Sql.list(connection, "SELECT id,name FROM " + kind.table + " ORDER BY id LIMIT 500",
                result -> new Entry(result.getLong("id"), result.getString("name")));
    }
    public long create(Connection connection, Kind kind, String name) throws SQLException {
        return Sql.insertId(connection, "INSERT INTO " + kind.table + "(name) VALUES (?) RETURNING id", name);
    }
    public boolean edit(Connection connection, Kind kind, long id, String name) throws SQLException {
        return Sql.update(connection, "UPDATE " + kind.table + " SET name=? WHERE id=?", name, id) == 1;
    }
    public boolean delete(Connection connection, Kind kind, long id) throws SQLException {
        return Sql.update(connection, "DELETE FROM " + kind.table + " WHERE id=?", id) == 1;
    }
}
