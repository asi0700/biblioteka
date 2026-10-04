package ru.library;

import ru.library.config.*;
import ru.library.repository.*;
import ru.library.security.PasswordHasher;
import ru.library.service.*;
import java.time.*;

public final class Backend {
    private final AuthService auth;
    private final UserService users;
    private final BookService books;
    private final ReaderService readers;
    private final LoanService loans;
    private final DictionaryService dictionaries;
    private final SettingsService settings;
    private final FineService fines;

    public Backend(ConnectionFactory connections, Clock clock) {
        java.util.Objects.requireNonNull(clock);
        Database database = new Database(connections);
        UserRepository userRepository = new UserRepository();
        BookRepository bookRepository = new BookRepository();
        ReaderRepository readerRepository = new ReaderRepository();
        LoanRepository loanRepository = new LoanRepository();
        FineRepository fineRepository = new FineRepository();
        SettingsRepository settingsRepository = new SettingsRepository();
        PasswordHasher passwords = new PasswordHasher();
        auth = new AuthService(database, userRepository, passwords, clock);
        users = new UserService(database, auth, userRepository, passwords);
        books = new BookService(database, auth, bookRepository, loanRepository);
        readers = new ReaderService(database, auth, readerRepository);
        loans = new LoanService(database, auth, bookRepository, readerRepository, loanRepository, fineRepository, settingsRepository, clock);
        dictionaries = new DictionaryService(database, auth, new DictionaryRepository());
        settings = new SettingsService(database, auth, settingsRepository);
        fines = new FineService(database, auth, fineRepository, loanRepository, readerRepository);
    }
    public static Backend fromEnvironment() {
        String zone = System.getenv("LIBRARY_ZONE");
        return new Backend(DatabaseConfig.fromEnvironment(),
                Clock.system(ZoneId.of(zone == null || zone.isBlank() ? "Europe/Moscow" : zone)));
    }
    public AuthService auth() { return auth; }
    public UserService users() { return users; }
    public BookService books() { return books; }
    public ReaderService readers() { return readers; }
    public LoanService loans() { return loans; }
    public DictionaryService dictionaries() { return dictionaries; }
    public SettingsService settings() { return settings; }
    public FineService fines() { return fines; }
}
