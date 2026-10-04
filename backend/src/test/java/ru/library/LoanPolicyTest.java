package ru.library;

import org.junit.jupiter.api.Test;
import ru.library.dto.LoanSettings;
import ru.library.exception.LibraryException;
import ru.library.service.LoanPolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class LoanPolicyTest {
    private final LocalDate due = LocalDate.of(2026, 9, 10);
    @Test void dueDateAndEarlyReturnHaveNoFine() {
        assertEquals(new BigDecimal("0.00"), LoanPolicy.fine(due, due, new BigDecimal("10.00")));
        assertEquals(new BigDecimal("0.00"), LoanPolicy.fine(due, due.minusDays(1), new BigDecimal("10.00")));
    }
    @Test void calculatesAcrossMonthBoundaryWithoutFloatingPointErrors() {
        assertEquals(new BigDecimal("20.10"),
                LoanPolicy.fine(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 3), new BigDecimal("6.70")));
    }
    @Test void acceptsZeroRateAndLimits() {
        assertEquals(new BigDecimal("0.00"), LoanPolicy.fine(due, due.plusDays(3), BigDecimal.ZERO));
        assertEquals(365, LoanPolicy.validate(new LoanSettings(365, new BigDecimal("10000.00"))).loanDays());
    }
    @Test void rejectsInvalidSettings() {
        assertThrows(LibraryException.class, () -> LoanPolicy.validate(new LoanSettings(0, BigDecimal.ONE)));
        assertThrows(LibraryException.class, () -> LoanPolicy.validate(new LoanSettings(366, BigDecimal.ONE)));
        assertThrows(LibraryException.class, () -> LoanPolicy.validate(new LoanSettings(14, new BigDecimal("-0.01"))));
        assertThrows(LibraryException.class, () -> LoanPolicy.validate(new LoanSettings(14, new BigDecimal("0.001"))));
    }
}
