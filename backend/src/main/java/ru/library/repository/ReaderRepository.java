package ru.library.repository;

import ru.library.dto.ReaderDraft;
import ru.library.model.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public final class ReaderRepository {
    private Reader map(ResultSet result) throws SQLException {
        return new Reader(result.getLong("id"), result.getString("full_name"), result.getString("phone"),
                result.getString("email"), result.getBoolean("active"));
    }
    private ReaderTicket ticket(ResultSet result) throws SQLException {
        return new ReaderTicket(result.getLong("id"), result.getLong("reader_id"), result.getString("ticket_number"),
                result.getObject("valid_from", LocalDate.class), result.getObject("valid_until", LocalDate.class), result.getBoolean("active"));
    }
    public Optional<Reader> find(Connection connection, long id, boolean lock) throws SQLException {
        return Sql.one(connection, lock ? "SELECT * FROM readers WHERE id=? FOR UPDATE" : "SELECT * FROM readers WHERE id=?", this::map, id);
    }
    public List<Reader> search(Connection connection, String pattern, int limit, int offset) throws SQLException {
        return Sql.list(connection, "SELECT * FROM readers WHERE full_name ILIKE ? ESCAPE '!' ORDER BY id LIMIT ? OFFSET ?", this::map, pattern, limit, offset);
    }
    public long create(Connection connection, ReaderDraft draft) throws SQLException {
        return Sql.insertId(connection, "INSERT INTO readers(full_name,phone,email) VALUES (?,?,?) RETURNING id", draft.fullName(), draft.phone(), draft.email());
    }
    public void edit(Connection connection, long id, ReaderDraft draft) throws SQLException {
        Sql.update(connection, "UPDATE readers SET full_name=?,phone=?,email=? WHERE id=?", draft.fullName(), draft.phone(), draft.email(), id);
    }
    public void active(Connection connection, long id, boolean active) throws SQLException {
        Sql.update(connection, "UPDATE readers SET active=? WHERE id=?", active, id);
    }
    public long issueTicket(Connection connection, long readerId, String number, LocalDate from, LocalDate until) throws SQLException {
        Sql.update(connection, "UPDATE reader_tickets SET active=FALSE WHERE reader_id=? AND active", readerId);
        return Sql.insertId(connection, "INSERT INTO reader_tickets(reader_id,ticket_number,valid_from,valid_until) VALUES (?,?,?,?) RETURNING id", readerId, number, from, until);
    }
    public boolean validTicket(Connection connection, long readerId, LocalDate today) throws SQLException {
        return Sql.one(connection, """
                SELECT id FROM reader_tickets WHERE reader_id=? AND active AND valid_from<=? AND valid_until>=? FOR SHARE
                """, result -> result.getLong(1), readerId, today, today).isPresent();
    }
    public List<ReaderTicket> tickets(Connection connection, long readerId) throws SQLException {
        return Sql.list(connection, "SELECT * FROM reader_tickets WHERE reader_id=? ORDER BY id", this::ticket, readerId);
    }
}
