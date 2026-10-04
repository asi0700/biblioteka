package ru.library.service;

import ru.library.config.Database;
import ru.library.dto.*;
import ru.library.model.DictionaryKind;
import ru.library.repository.DictionaryRepository;
import ru.library.exception.LibraryException;
import ru.library.security.Validation;
import java.util.List;
import static ru.library.exception.LibraryException.Code.NOT_FOUND;

public final class DictionaryService {
    private final Database database;
    private final AuthService auth;
    private final DictionaryRepository dictionaries;
    public DictionaryService(Database database, AuthService auth, DictionaryRepository dictionaries) {
        this.database = database; this.auth = auth; this.dictionaries = dictionaries;
    }
    private static DictionaryRepository.Kind kind(DictionaryKind value) {
        return switch (Validation.required(value, "справочник")) {
            case AUTHOR -> DictionaryRepository.Kind.AUTHOR;
            case GENRE -> DictionaryRepository.Kind.GENRE;
            case PUBLISHER -> DictionaryRepository.Kind.PUBLISHER;
        };
    }
    public List<DictionaryEntry> list(SessionToken token, DictionaryKind value) {
        var kind = kind(value);
        return database.transaction(c -> {
            auth.require(c, token, false);
            return dictionaries.list(c, kind).stream().map(entry -> new DictionaryEntry(entry.id(), entry.name())).toList();
        });
    }
    public long create(SessionToken token, DictionaryKind value, String name) {
        var kind = kind(value);
        String clean = Validation.text(name, kind.maxLength(), "название");
        return database.transaction(c -> { auth.require(c, token, true); return dictionaries.create(c, kind, clean); });
    }
    public void update(SessionToken token, DictionaryKind value, long id, String name) {
        var kind = kind(value); Validation.id(id);
        String clean = Validation.text(name, kind.maxLength(), "название");
        database.transaction(c -> {
            auth.require(c, token, true);
            if (!dictionaries.edit(c, kind, id, clean)) throw new LibraryException(NOT_FOUND, "Запись справочника не найдена.");
            return null;
        });
    }
    public void delete(SessionToken token, DictionaryKind value, long id) {
        var kind = kind(value); Validation.id(id);
        database.transaction(c -> {
            auth.require(c, token, true);
            if (!dictionaries.delete(c, kind, id)) throw new LibraryException(NOT_FOUND, "Запись справочника не найдена.");
            return null;
        });
    }
}
