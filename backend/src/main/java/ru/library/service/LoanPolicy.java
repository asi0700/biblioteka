package ru.library.service;

import ru.library.dto.LoanSettings;
import ru.library.exception.LibraryException;
import java.math.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import static ru.library.exception.LibraryException.Code.VALIDATION;

public final class LoanPolicy {
    private LoanPolicy() { }
    public static LoanSettings validate(LoanSettings settings) {
        if (settings == null || settings.loanDays() < 1 || settings.loanDays() > 365 ||
                settings.dailyFineRate() == null || settings.dailyFineRate().signum() < 0 ||
                settings.dailyFineRate().compareTo(new BigDecimal("10000")) > 0)
            throw new LibraryException(VALIDATION, "Некорректные настройки выдачи или штрафа.");
        try { return new LoanSettings(settings.loanDays(), settings.dailyFineRate().setScale(2, RoundingMode.UNNECESSARY)); }
        catch (ArithmeticException exception) { throw new LibraryException(VALIDATION, "Ставка задаётся с точностью до копеек."); }
    }
    public static BigDecimal fine(LocalDate dueDate, LocalDate returnDate, BigDecimal dailyRate) {
        long days = Math.max(0, ChronoUnit.DAYS.between(dueDate, returnDate));
        return dailyRate.multiply(BigDecimal.valueOf(days)).setScale(2, RoundingMode.UNNECESSARY);
    }
}
