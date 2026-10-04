package ru.library.repository;

import ru.library.dto.LoanSettings;
import java.sql.*;
import java.math.BigDecimal;

public final class SettingsRepository {
    public LoanSettings get(Connection connection) throws SQLException {
        var rows = Sql.list(connection, "SELECT key,value FROM settings ORDER BY key FOR SHARE",
                result -> new ru.library.model.Setting(result.getString("key"), result.getBigDecimal("value")));
        Integer days = null;
        BigDecimal rate = null;
        for (var row : rows) {
            if (row.key().equals("loan_days")) days = row.value().intValueExact();
            if (row.key().equals("daily_fine_rate")) rate = row.value();
        }
        if (days == null || rate == null) throw new SQLException("Нет обязательных настроек.");
        return new LoanSettings(days, rate);
    }
    public void set(Connection connection, LoanSettings settings) throws SQLException {
        Sql.update(connection, "UPDATE settings SET value=? WHERE key='daily_fine_rate'", settings.dailyFineRate());
        Sql.update(connection, "UPDATE settings SET value=? WHERE key='loan_days'", settings.loanDays());
    }
}
