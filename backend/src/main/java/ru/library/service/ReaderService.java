package ru.library.service;

import ru.library.config.Database;
import ru.library.dto.*;
import ru.library.exception.LibraryException;
import ru.library.model.*;
import ru.library.repository.ReaderRepository;
import ru.library.security.Validation;
import java.time.LocalDate;
import java.util.List;
import static ru.library.exception.LibraryException.Code.*;

public final class ReaderService {
    private final Database database;
    private final AuthService auth;
    private final ReaderRepository readers;
    public ReaderService(Database database, AuthService auth, ReaderRepository readers) {
        this.database = database; this.auth = auth; this.readers = readers;
    }
    public List<Reader> search(SessionToken token, String query, int limit, int offset) {
        Validation.page(limit, offset);
        String pattern = Validation.pattern(query);
        return database.transaction(c -> { auth.require(c, token, false); return readers.search(c, pattern, limit, offset); });
    }
    public Reader get(SessionToken token, long id) {
        Validation.id(id);
        return database.transaction(c -> {
            auth.require(c, token, false);
            return readers.find(c, id, false).orElseThrow(() -> new LibraryException(NOT_FOUND, "Читатель не найден."));
        });
    }
    public Reader create(SessionToken token, ReaderDraft draft) {
        ReaderDraft clean = validate(draft);
        return database.transaction(c -> {
            auth.require(c, token, true);
            long id = readers.create(c, clean);
            return readers.find(c, id, false).orElseThrow();
        });
    }
    public Reader update(SessionToken token, long id, ReaderDraft draft) {
        Validation.id(id);
        ReaderDraft clean = validate(draft);
        return database.transaction(c -> {
            auth.require(c, token, true);
            readers.find(c, id, true).orElseThrow(() -> new LibraryException(NOT_FOUND, "Читатель не найден."));
            readers.edit(c, id, clean);
            return readers.find(c, id, false).orElseThrow();
        });
    }
    public void setActive(SessionToken token, long id, boolean active) {
        Validation.id(id);
        database.transaction(c -> {
            auth.require(c, token, true);
            readers.find(c, id, true).orElseThrow(() -> new LibraryException(NOT_FOUND, "Читатель не найден."));
            readers.active(c, id, active);
            return null;
        });
    }
    public List<ReaderTicket> tickets(SessionToken token, long readerId) {
        Validation.id(readerId);
        return database.transaction(c -> { auth.require(c, token, false); return readers.tickets(c, readerId); });
    }
    public long issueTicket(SessionToken token, long readerId, String ticketNumber, LocalDate from, LocalDate until) {
        Validation.id(readerId);
        String number = Validation.text(ticketNumber, 50, "номер билета");
        Validation.required(from, "начало действия билета");
        Validation.required(until, "окончание действия билета");
        if (until.isBefore(from)) throw new LibraryException(VALIDATION, "Неверный срок действия билета.");
        return database.transaction(c -> {
            auth.require(c, token, true);
            Reader reader = readers.find(c, readerId, true).orElseThrow(() -> new LibraryException(NOT_FOUND, "Читатель не найден."));
            if (!reader.active()) throw new LibraryException(CONFLICT, "Читатель отключён.");
            return readers.issueTicket(c, readerId, number, from, until);
        });
    }
    private static ReaderDraft validate(ReaderDraft draft) {
        Validation.required(draft, "читатель");
        return new ReaderDraft(Validation.text(draft.fullName(), 200, "ФИО"),
                Validation.optional(draft.phone(), 30, "телефон"), Validation.optional(draft.email(), 254, "email"));
    }
}
