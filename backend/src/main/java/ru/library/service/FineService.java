package ru.library.service;

import ru.library.config.Database;
import ru.library.dto.SessionToken;
import ru.library.exception.LibraryException;
import ru.library.model.*;
import ru.library.repository.*;
import ru.library.security.Validation;
import java.util.List;
import static ru.library.exception.LibraryException.Code.*;

public final class FineService {
    private final Database database;
    private final AuthService auth;
    private final FineRepository fines;
    private final LoanRepository loans;
    private final ReaderRepository readers;
    public FineService(Database database, AuthService auth, FineRepository fines, LoanRepository loans, ReaderRepository readers) {
        this.database = database; this.auth = auth; this.fines = fines; this.loans = loans; this.readers = readers;
    }
    public List<Fine> list(SessionToken token, long readerId, int limit, int offset) {
        Validation.id(readerId); Validation.page(limit, offset);
        return database.transaction(c -> { auth.require(c, token, false); return fines.list(c, readerId, limit, offset); });
    }
    public void pay(SessionToken token, long fineId) {
        Validation.id(fineId);
        database.transaction(c -> {
            User employee = auth.require(c, token, true);
            Fine snapshot = fines.find(c, fineId, false).orElseThrow(() -> new LibraryException(NOT_FOUND, "Штраф не найден."));
            Loan loan = loans.find(c, snapshot.loanId(), false).orElseThrow();
            readers.find(c, loan.readerId(), true).orElseThrow();
            Fine fine = fines.find(c, fineId, true).orElseThrow();
            if (fine.paidAt() != null) throw new LibraryException(CONFLICT, "Штраф уже оплачен.");
            fines.pay(c, fineId, employee.id());
            return null;
        });
    }
}
