package ru.library.repository;

import ru.library.model.*;
import java.sql.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;

public final class LoanRepository {
    private Loan map(ResultSet result) throws SQLException {
        return new Loan(result.getLong("id"), result.getLong("copy_id"), result.getLong("reader_id"),
                result.getLong("issued_by"), Sql.nullableLong(result, "returned_by"), result.getString("status_code"),
                result.getObject("issue_date", LocalDate.class), result.getObject("planned_return_date", LocalDate.class),
                result.getObject("actual_return_date", LocalDate.class), result.getBigDecimal("daily_fine_rate"));
    }
    public Optional<Loan> find(Connection connection, long id, boolean lock) throws SQLException {
        return Sql.one(connection, lock ? "SELECT * FROM loans WHERE id=? FOR UPDATE" : "SELECT * FROM loans WHERE id=?", this::map, id);
    }
    public boolean activeCopy(Connection connection, long copyId) throws SQLException {
        return Sql.one(connection, "SELECT id FROM loans WHERE copy_id=? AND actual_return_date IS NULL", result -> result.getLong(1), copyId).isPresent();
    }
    public long issue(Connection connection, long copyId, long readerId, long userId, LocalDate date, LocalDate due, BigDecimal rate) throws SQLException {
        return Sql.insertId(connection, """
                INSERT INTO loans(copy_id,reader_id,issued_by,status_code,issue_date,planned_return_date,daily_fine_rate)
                VALUES (?,?,?,'active',?,?,?) RETURNING id
                """, copyId, readerId, userId, date, due, rate);
    }
    public void returned(Connection connection, long loanId, long userId, LocalDate date) throws SQLException {
        Sql.update(connection, "UPDATE loans SET actual_return_date=?,returned_by=?,status_code='returned' WHERE id=?", date, userId, loanId);
    }
    public List<Loan> history(Connection connection, long readerId, int limit, int offset) throws SQLException {
        return Sql.list(connection, "SELECT * FROM loans WHERE reader_id=? ORDER BY id DESC LIMIT ? OFFSET ?", this::map, readerId, limit, offset);
    }
    public List<Loan> overdue(Connection connection, LocalDate today, int limit, int offset) throws SQLException {
        return Sql.list(connection, """
                SELECT * FROM loans WHERE actual_return_date IS NULL AND planned_return_date<? ORDER BY planned_return_date,id LIMIT ? OFFSET ?
                """, this::map, today, limit, offset);
    }
    public List<LoanStatus> statuses(Connection connection) throws SQLException {
        return Sql.list(connection, "SELECT * FROM loan_statuses ORDER BY id",
                result -> new LoanStatus(result.getLong("id"), result.getString("code"), result.getString("name")));
    }
    public ru.library.dto.LibraryStatistics statistics(Connection connection, LocalDate today) throws SQLException {
        return Sql.one(connection, """
                SELECT (SELECT count(*) FROM books WHERE NOT archived) AS books,
                       (SELECT count(*) FROM book_copies WHERE condition='usable') AS copies,
                       (SELECT count(*) FROM readers WHERE active) AS readers,
                       (SELECT count(*) FROM loans WHERE actual_return_date IS NULL) AS active,
                       (SELECT count(*) FROM loans WHERE actual_return_date IS NULL AND planned_return_date<?) AS overdue,
                       (SELECT coalesce(sum(amount),0) FROM fines WHERE paid_at IS NULL) AS debt
                """, result -> new ru.library.dto.LibraryStatistics(result.getLong("books"), result.getLong("copies"),
                result.getLong("readers"), result.getLong("active"), result.getLong("overdue"), result.getBigDecimal("debt")), today).orElseThrow();
    }
}
