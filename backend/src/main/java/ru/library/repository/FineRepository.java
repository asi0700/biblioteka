package ru.library.repository;

import ru.library.model.Fine;
import java.sql.*;
import java.math.BigDecimal;
import java.util.*;

public final class FineRepository {
    private Fine map(ResultSet result) throws SQLException {
        return new Fine(result.getLong("id"), result.getLong("loan_id"), result.getBigDecimal("amount"),
                Sql.instant(result, "paid_at"), Sql.nullableLong(result, "paid_by"));
    }
    public boolean unpaid(Connection connection, long readerId) throws SQLException {
        return Sql.one(connection, """
                SELECT f.id FROM fines f JOIN loans l ON l.id=f.loan_id WHERE l.reader_id=? AND f.paid_at IS NULL LIMIT 1
                """, result -> result.getLong(1), readerId).isPresent();
    }
    public long create(Connection connection, long loanId, BigDecimal amount) throws SQLException {
        return Sql.insertId(connection, "INSERT INTO fines(loan_id,amount) VALUES (?,?) RETURNING id", loanId, amount);
    }
    public Optional<Fine> find(Connection connection, long id, boolean lock) throws SQLException {
        return Sql.one(connection, lock ? "SELECT * FROM fines WHERE id=? FOR UPDATE" : "SELECT * FROM fines WHERE id=?", this::map, id);
    }
    public List<Fine> list(Connection connection, long readerId, int limit, int offset) throws SQLException {
        return Sql.list(connection, "SELECT f.* FROM fines f JOIN loans l ON l.id=f.loan_id WHERE l.reader_id=? ORDER BY f.id DESC LIMIT ? OFFSET ?",
                this::map, readerId, limit, offset);
    }
    public void pay(Connection connection, long id, long userId) throws SQLException {
        Sql.update(connection, "UPDATE fines SET paid_at=CURRENT_TIMESTAMP,paid_by=? WHERE id=? AND paid_at IS NULL", userId, id);
    }
}
