package ru.library.config;

import ru.library.exception.LibraryException;
import java.sql.Connection;
import java.sql.SQLException;
import static ru.library.exception.LibraryException.Code.*;

public final class Database {
    @FunctionalInterface
    public interface Work<T> { T run(Connection connection) throws SQLException; }
    private final ConnectionFactory factory;

    public Database(ConnectionFactory factory) {
        this.factory = java.util.Objects.requireNonNull(factory);
    }

    public <T> T transaction(Work<T> work) {
        try (Connection connection = factory.open()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                try { connection.rollback(); }
                catch (SQLException rollbackError) { exception.addSuppressed(rollbackError); }
                throw exception;
            }
        } catch (SQLException exception) {
            String state = exception.getSQLState();
            if (state != null && (state.startsWith("23") || state.equals("40001") || state.equals("40P01"))) {
                throw new LibraryException(CONFLICT, "Операция конфликтует с текущими данными. Обновите данные и повторите.", exception);
            }
            throw new LibraryException(STORAGE, "Не удалось выполнить операцию с базой данных.", exception);
        }
    }
}
