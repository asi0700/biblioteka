package ru.library.service;

import ru.library.config.Database;
import ru.library.dto.*;
import ru.library.exception.LibraryException;
import ru.library.model.*;
import ru.library.repository.*;
import ru.library.security.Validation;
import java.util.List;
import static ru.library.exception.LibraryException.Code.*;

public final class BookService {
    private final Database database;
    private final AuthService auth;
    private final BookRepository books;
    private final LoanRepository loans;
    public BookService(Database database, AuthService auth, BookRepository books, LoanRepository loans) {
        this.database = database; this.auth = auth; this.books = books; this.loans = loans;
    }
    public List<Book> search(SessionToken token, String query, int limit, int offset) {
        Validation.page(limit, offset);
        String pattern = Validation.pattern(query);
        return database.transaction(c -> { auth.require(c, token, false); return books.search(c, pattern, limit, offset); });
    }
    public List<CatalogEntry> catalog(SessionToken token, String query, int limit, int offset) {
        Validation.page(limit, offset);
        String pattern = Validation.pattern(query);
        return database.transaction(c -> { auth.require(c, token, false); return books.catalog(c, pattern, limit, offset); });
    }
    public Book get(SessionToken token, long id) {
        Validation.id(id);
        return database.transaction(c -> {
            auth.require(c, token, false);
            return books.find(c, id, false).orElseThrow(() -> new LibraryException(NOT_FOUND, "Книга не найдена."));
        });
    }
    public Book create(SessionToken token, BookDraft draft) {
        BookDraft clean = validate(draft);
        return database.transaction(c -> {
            auth.require(c, token, true);
            long id = books.create(c, clean);
            return books.find(c, id, false).orElseThrow();
        });
    }
    public Book update(SessionToken token, long id, BookDraft draft) {
        Validation.id(id);
        BookDraft clean = validate(draft);
        return database.transaction(c -> {
            auth.require(c, token, true);
            books.find(c, id, true).orElseThrow(() -> new LibraryException(NOT_FOUND, "Книга не найдена."));
            books.edit(c, id, clean);
            return books.find(c, id, false).orElseThrow();
        });
    }
    public void archive(SessionToken token, long id, boolean archived) {
        Validation.id(id);
        database.transaction(c -> {
            auth.require(c, token, true);
            books.find(c, id, true).orElseThrow(() -> new LibraryException(NOT_FOUND, "Книга не найдена."));
            books.archive(c, id, archived);
            return null;
        });
    }
    public List<BookCopy> copies(SessionToken token, long bookId) {
        Validation.id(bookId);
        return database.transaction(c -> { auth.require(c, token, false); return books.copies(c, bookId); });
    }
    public long availableCopies(SessionToken token, long bookId) {
        Validation.id(bookId);
        return database.transaction(c -> { auth.require(c, token, false); return books.available(c, bookId); });
    }
    public BookCopy addCopy(SessionToken token, long bookId, String inventoryNumber) {
        Validation.id(bookId);
        String number = Validation.text(inventoryNumber, 50, "инвентарный номер");
        return database.transaction(c -> {
            auth.require(c, token, true);
            Book book = books.find(c, bookId, true).orElseThrow(() -> new LibraryException(NOT_FOUND, "Книга не найдена."));
            if (book.archived()) throw new LibraryException(CONFLICT, "Книга находится в архиве.");
            long id = books.addCopy(c, bookId, number);
            return books.copyById(c, id, false).orElseThrow();
        });
    }
    public void setCopyCondition(SessionToken token, long copyId, CopyCondition condition) {
        Validation.id(copyId);
        Validation.required(condition, "состояние экземпляра");
        database.transaction(c -> {
            auth.require(c, token, true);
            books.copyById(c, copyId, true).orElseThrow(() -> new LibraryException(NOT_FOUND, "Экземпляр не найден."));
            if (loans.activeCopy(c, copyId)) throw new LibraryException(CONFLICT, "Экземпляр сейчас выдан читателю.");
            books.condition(c, copyId, condition);
            return null;
        });
    }
    private static BookDraft validate(BookDraft draft) {
        Validation.required(draft, "книга");
        if (draft.publicationYear() != null && (draft.publicationYear() < 1 || draft.publicationYear() > 9999))
            throw new LibraryException(VALIDATION, "Некорректный год издания.");
        if (draft.genreId() != null) Validation.id(draft.genreId());
        if (draft.publisherId() != null) Validation.id(draft.publisherId());
        for (Long id : draft.authorIds()) Validation.id(id);
        if (draft.authorIds().stream().distinct().count() != draft.authorIds().size())
            throw new LibraryException(VALIDATION, "Автор указан несколько раз.");
        String description = draft.description() == null ? "" : draft.description().strip();
        if (description.length() > 10000) throw new LibraryException(VALIDATION, "Описание слишком длинное.");
        return new BookDraft(Validation.text(draft.title(), 300, "название"),
                Validation.optional(draft.isbn(), 20, "ISBN"), draft.publicationYear(),
                draft.genreId(), draft.publisherId(), description, draft.authorIds());
    }
}
