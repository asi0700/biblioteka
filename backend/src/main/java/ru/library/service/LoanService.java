package ru.library.service;

import ru.library.config.Database;
import ru.library.dto.*;
import ru.library.exception.LibraryException;
import ru.library.model.*;
import ru.library.repository.*;
import ru.library.security.Validation;
import java.time.*;
import java.math.BigDecimal;
import java.util.List;
import static ru.library.exception.LibraryException.Code.*;

public final class LoanService {
    private final Database database;
    private final AuthService auth;
    private final BookRepository books;
    private final ReaderRepository readers;
    private final LoanRepository loans;
    private final FineRepository fines;
    private final SettingsRepository settings;
    private final Clock clock;
    public LoanService(Database database, AuthService auth, BookRepository books, ReaderRepository readers,
                       LoanRepository loans, FineRepository fines, SettingsRepository settings, Clock clock) {
        this.database = database; this.auth = auth; this.books = books; this.readers = readers;
        this.loans = loans; this.fines = fines; this.settings = settings; this.clock = clock;
    }

    public Loan issue(SessionToken token, long bookId, long readerId) {
        Validation.id(bookId);
        Validation.id(readerId);
        return database.transaction(c -> {
            User employee = auth.require(c, token, false);
            LocalDate today = LocalDate.now(clock);
            Reader reader = readers.find(c, readerId, true).orElseThrow(() -> new LibraryException(NOT_FOUND, "Читатель не найден."));
            if (!reader.active()) throw new LibraryException(CONFLICT, "Читатель отключён.");
            if (!readers.validTicket(c, readerId, today)) throw new LibraryException(CONFLICT, "Нет действующего читательского билета.");
            if (fines.unpaid(c, readerId)) throw new LibraryException(CONFLICT, "У читателя есть неоплаченные штрафы.");
            Book book = books.find(c, bookId, true).orElseThrow(() -> new LibraryException(NOT_FOUND, "Книга не найдена."));
            if (book.archived()) throw new LibraryException(CONFLICT, "Книга находится в архиве.");
            LoanSettings policy = settings.get(c);
            BookCopy copy = books.freeCopy(c, bookId).orElseThrow(() -> new LibraryException(CONFLICT, "Свободных экземпляров нет. Обновите данные и повторите."));
            long id = loans.issue(c, copy.id(), readerId, employee.id(), today,
                    today.plusDays(policy.loanDays()), policy.dailyFineRate());
            return loans.find(c, id, false).orElseThrow();
        });
    }

    // Возврат и начисление штрафа либо сохраняются вместе, либо полностью отменяются.
    public Loan returnBook(SessionToken token, long loanId) {
        Validation.id(loanId);
        return database.transaction(c -> {
            User employee = auth.require(c, token, false);
            Loan snapshot = loans.find(c, loanId, false).orElseThrow(() -> new LibraryException(NOT_FOUND, "Выдача не найдена."));
            readers.find(c, snapshot.readerId(), true).orElseThrow();
            Loan loan = loans.find(c, loanId, true).orElseThrow();
            books.copyById(c, loan.copyId(), true).orElseThrow();
            if (loan.actualReturnDate() != null) throw new LibraryException(CONFLICT, "Книга уже возвращена.");
            LocalDate today = LocalDate.now(clock);
            if (today.isBefore(loan.issueDate())) throw new LibraryException(CONFLICT, "Дата возврата раньше даты выдачи.");
            loans.returned(c, loanId, employee.id(), today);
            BigDecimal amount = LoanPolicy.fine(loan.plannedReturnDate(), today, loan.dailyFineRate());
            if (amount.signum() > 0) fines.create(c, loanId, amount);
            return loans.find(c, loanId, false).orElseThrow();
        });
    }
    public List<Loan> history(SessionToken token, long readerId, int limit, int offset) {
        Validation.id(readerId); Validation.page(limit, offset);
        return database.transaction(c -> { auth.require(c, token, false); return loans.history(c, readerId, limit, offset); });
    }
    public List<Loan> overdue(SessionToken token, int limit, int offset) {
        Validation.page(limit, offset);
        return database.transaction(c -> { auth.require(c, token, false); return loans.overdue(c, LocalDate.now(clock), limit, offset); });
    }
    public LibraryStatistics statistics(SessionToken token) {
        return database.transaction(c -> { auth.require(c, token, false); return loans.statistics(c, LocalDate.now(clock)); });
    }
    public List<LoanStatus> statuses(SessionToken token) {
        return database.transaction(c -> { auth.require(c, token, false); return loans.statuses(c); });
    }
}
