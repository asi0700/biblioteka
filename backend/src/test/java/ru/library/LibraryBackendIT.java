package ru.library;

import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.library.bootstrap.AdminBootstrap;
import ru.library.config.*;
import ru.library.dto.*;
import ru.library.exception.LibraryException;
import ru.library.model.*;
import ru.library.repository.Sql;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static ru.library.exception.LibraryException.Code.*;

class LibraryBackendIT {
    private static PostgreSQLContainer<?> postgres;
    private static ConnectionFactory baseConnections;
    private static ConnectionFactory connections;
    private static String schema;
    private Backend backend;
    private MutableClock clock;
    private SessionToken admin;
    private SessionToken employee;
    private User employeeUser;
    private String adminPassword;
    private String employeePassword;

    @BeforeAll static void startDatabase() throws Exception {
        String localUrl = System.getenv("LIBRARY_TEST_DB_URL");
        if (localUrl == null || localUrl.isBlank()) {
            postgres = new PostgreSQLContainer<>("postgres:17-alpine");
            postgres.start();
            baseConnections = () -> DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        } else {
            String user = System.getenv("LIBRARY_TEST_DB_USER");
            String password = System.getenv("LIBRARY_TEST_DB_PASSWORD");
            baseConnections = () -> DriverManager.getConnection(localUrl, user, password == null ? "" : password);
        }
        schema = "library_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = baseConnections.open(); Statement statement = c.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        connections = () -> {
            Connection c = baseConnections.open();
            try { c.setSchema(schema); return c; }
            catch (SQLException exception) { c.close(); throw exception; }
        };
        try (Connection c = connections.open(); Statement statement = c.createStatement()) {
            statement.execute(resource("db/schema.sql"));
        }
    }

    @AfterAll static void stopDatabase() throws Exception {
        try {
            if (baseConnections != null && schema != null && schema.matches("library_test_[a-f0-9]{32}")) {
                try (Connection c = baseConnections.open(); Statement statement = c.createStatement()) {
                    statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
                }
            }
        } finally { if (postgres != null) postgres.stop(); }
    }

    @BeforeEach void prepare() throws Exception {
        try (Connection c = connections.open(); Statement statement = c.createStatement()) {
            statement.execute("""
                    TRUNCATE roles,users,authors,genres,publishers,books,book_authors,book_copies,
                    readers,reader_tickets,loan_statuses,loans,fines,settings RESTART IDENTITY CASCADE
                    """);
            statement.execute(resource("db/reference-data.sql"));
        }
        clock = new MutableClock(Instant.parse("2026-09-01T09:00:00Z"), ZoneId.of("Europe/Moscow"));
        adminPassword = UUID.randomUUID().toString();
        employeePassword = UUID.randomUUID().toString();
        AdminBootstrap.createFirst(connections, "admin", adminPassword, "Администратор");
        backend = new Backend(connections, clock);
        admin = backend.auth().login("admin", adminPassword).token();
        employeeUser = backend.users().create(admin, "employee", employeePassword, "Сотрудник", Role.USER);
        employee = backend.auth().login("employee", employeePassword).token();
    }

    @Test void schemaHasExactlyFourteenTablesAndReferencesCanBeAppliedAgain() throws Exception {
        try (Connection c = connections.open(); Statement statement = c.createStatement()) {
            assertEquals(14L, Sql.one(c, "SELECT count(*) FROM information_schema.tables WHERE table_schema=?", r -> r.getLong(1), schema).orElseThrow());
            statement.execute(resource("db/reference-data.sql"));
            assertEquals(2, backend.users().roles(admin).size());
        }
    }

    @Test void loginChecksPasswordAndBackendPermissions() {
        assertEquals(Role.USER, backend.auth().currentUser(employee).role());
        fails(UNAUTHENTICATED, () -> backend.auth().login("employee", UUID.randomUUID().toString()));
        fails(UNAUTHENTICATED, () -> backend.auth().login("missing", employeePassword));
        fails(FORBIDDEN, () -> backend.books().create(employee, draft("Книга")));
        fails(FORBIDDEN, () -> backend.readers().create(employee, new ReaderDraft("Читатель", null, null)));
        fails(FORBIDDEN, () -> backend.users().resetPassword(employee, employeeUser.id(), UUID.randomUUID().toString()));
        fails(FORBIDDEN, () -> backend.settings().update(employee, new LoanSettings(14, BigDecimal.ONE)));
        fails(UNAUTHENTICATED, () -> backend.books().search(new SessionToken(UUID.randomUUID().toString()), "", 20, 0));
    }

    @Test void passwordResetInvalidatesOldSessionAndOldPassword() {
        String replacement = UUID.randomUUID().toString();
        backend.users().resetPassword(admin, employeeUser.id(), replacement);
        fails(UNAUTHENTICATED, () -> backend.books().search(employee, "", 20, 0));
        fails(UNAUTHENTICATED, () -> backend.auth().login("employee", employeePassword));
        assertEquals(employeeUser.id(), backend.auth().login("employee", replacement).user().id());
    }

    @Test void ownPasswordChangeRequiresCurrentPassword() {
        String replacement = UUID.randomUUID().toString();
        fails(UNAUTHENTICATED, () -> backend.users().changeOwnPassword(employee, UUID.randomUUID().toString(), replacement));
        backend.users().changeOwnPassword(employee, employeePassword, replacement);
        fails(UNAUTHENTICATED, () -> backend.auth().currentUser(employee));
        assertEquals(employeeUser.id(), backend.auth().login("employee", replacement).user().id());
    }

    @Test void disablingUserAndChangingRoleInvalidateSessions() {
        backend.users().setAccess(admin, employeeUser.id(), Role.ADMIN, true);
        fails(UNAUTHENTICATED, () -> backend.auth().currentUser(employee));
        employee = backend.auth().login("employee", employeePassword).token();
        assertEquals(Role.ADMIN, backend.auth().currentUser(employee).role());
        backend.users().setAccess(admin, employeeUser.id(), Role.USER, false);
        fails(UNAUTHENTICATED, () -> backend.auth().currentUser(employee));
        fails(UNAUTHENTICATED, () -> backend.auth().login("employee", employeePassword));
    }

    @Test void protectsLastAdminAndDisallowsRepeatedBootstrap() {
        long adminId = backend.auth().currentUser(admin).id();
        fails(CONFLICT, () -> backend.users().setAccess(admin, adminId, Role.USER, true));
        fails(CONFLICT, () -> backend.users().setAccess(admin, adminId, Role.ADMIN, false));
        fails(CONFLICT, () -> AdminBootstrap.createFirst(connections, "another", UUID.randomUUID().toString(), "Другой"));
    }

    @Test void logoutAndExpiryRevokeSessions() {
        backend.auth().logout(employee);
        fails(UNAUTHENTICATED, () -> backend.auth().currentUser(employee));
        employee = backend.auth().login("employee", employeePassword).token();
        clock.advance(Duration.ofHours(8));
        fails(UNAUTHENTICATED, () -> backend.auth().currentUser(employee));
    }

    @Test void catalogStoresAuthorsAndTreatsInjectionAsData() {
        String name = "O'Connor'; DROP TABLE books; --";
        long author = backend.dictionaries().create(admin, DictionaryKind.AUTHOR, name);
        Book book = backend.books().create(admin, new BookDraft("SQL %_!", null, 2020, null, null, "", List.of(author)));
        assertEquals(List.of(author), backend.books().get(employee, book.id()).authorIds());
        assertEquals(1, backend.books().search(employee, "DROP TABLE", 20, 0).size());
        assertEquals(1, backend.books().search(employee, "%_!", 20, 0).size());
        assertTrue(backend.books().search(employee, "' OR 1=1 --", 20, 0).isEmpty());
        fails(UNAUTHENTICATED, () -> backend.auth().login("' OR 1=1 --", employeePassword));
        assertEquals(1, backend.books().search(employee, "", 20, 0).size());
    }

    @Test void failedBookCreationRollsBackWholeTransaction() {
        fails(CONFLICT, () -> backend.books().create(admin,
                new BookDraft("Не сохранится", null, null, null, null, "", List.of(999999L))));
        assertTrue(backend.books().search(admin, "", 20, 0).isEmpty());
    }

    @Test void cannotDeleteReferencedDictionaryAndCanEditCatalog() {
        long genre = backend.dictionaries().create(admin, DictionaryKind.GENRE, "Жанр");
        Book book = backend.books().create(admin, new BookDraft("Название", null, null, genre, null, "", List.of()));
        fails(CONFLICT, () -> backend.dictionaries().delete(admin, DictionaryKind.GENRE, genre));
        backend.books().update(admin, book.id(), draft("Обновлённое название"));
        assertEquals("Обновлённое название", backend.books().get(employee, book.id()).title());
        backend.dictionaries().delete(admin, DictionaryKind.GENRE, genre);
    }

    @Test void issuanceChecksTicketValidityAndReaderStatus() {
        Book book = bookWithCopy();
        Reader reader = backend.readers().create(admin, new ReaderDraft("Читатель", null, null));
        fails(CONFLICT, () -> backend.loans().issue(employee, book.id(), reader.id()));
        LocalDate today = LocalDate.now(clock);
        backend.readers().issueTicket(admin, reader.id(), "EXPIRED", today.minusDays(30), today.minusDays(1));
        fails(CONFLICT, () -> backend.loans().issue(employee, book.id(), reader.id()));
        backend.readers().issueTicket(admin, reader.id(), "FUTURE", today.plusDays(1), today.plusDays(30));
        fails(CONFLICT, () -> backend.loans().issue(employee, book.id(), reader.id()));
        backend.readers().issueTicket(admin, reader.id(), "CURRENT", today, today.plusDays(30));
        backend.readers().setActive(admin, reader.id(), false);
        fails(CONFLICT, () -> backend.loans().issue(employee, book.id(), reader.id()));
        backend.readers().setActive(admin, reader.id(), true);
        assertNotNull(backend.loans().issue(employee, book.id(), reader.id()));
    }

    @Test void issuanceAndTimelyReturnUpdateAvailabilityAndHistory() {
        Book book = bookWithCopy();
        Reader reader = readerWithTicket();
        Loan loan = backend.loans().issue(employee, book.id(), reader.id());
        assertEquals(LocalDate.now(clock).plusDays(14), loan.plannedReturnDate());
        assertEquals(0, backend.books().availableCopies(employee, book.id()));
        fails(CONFLICT, () -> backend.loans().issue(employee, book.id(), reader.id()));
        BookCopy copy = backend.books().copies(admin, book.id()).getFirst();
        fails(CONFLICT, () -> backend.books().setCopyCondition(admin, copy.id(), CopyCondition.REPAIR));
        Loan returned = backend.loans().returnBook(employee, loan.id());
        assertEquals("returned", returned.statusCode());
        assertEquals(LocalDate.now(clock), returned.actualReturnDate());
        assertEquals(1, backend.books().availableCopies(employee, book.id()));
        assertTrue(backend.fines().list(employee, reader.id(), 20, 0).isEmpty());
        assertEquals(1, backend.loans().history(employee, reader.id(), 20, 0).size());
        fails(CONFLICT, () -> backend.loans().returnBook(employee, loan.id()));
    }

    @Test void overdueReturnCreatesFineUsingOriginalRateAndBlocksNewLoan() {
        Book book = bookWithCopy();
        Reader reader = readerWithTicket();
        Loan loan = backend.loans().issue(employee, book.id(), reader.id());
        backend.settings().update(admin, new LoanSettings(7, new BigDecimal("99.00")));
        clock.advance(Duration.ofDays(17));
        relogin();
        assertEquals(1, backend.loans().overdue(employee, 20, 0).size());
        assertEquals(1, backend.loans().statistics(employee).overdueLoans());
        backend.loans().returnBook(employee, loan.id());
        Fine fine = backend.fines().list(employee, reader.id(), 20, 0).getFirst();
        assertEquals(new BigDecimal("30.00"), fine.amount());
        fails(CONFLICT, () -> backend.loans().issue(employee, book.id(), reader.id()));
        fails(FORBIDDEN, () -> backend.fines().pay(employee, fine.id()));
        backend.fines().pay(admin, fine.id());
        fails(CONFLICT, () -> backend.fines().pay(admin, fine.id()));
        assertEquals(LocalDate.now(clock).plusDays(7), backend.loans().issue(employee, book.id(), reader.id()).plannedReturnDate());
    }

    @Test void archivedBooksAndUnavailableCopiesCannotBeIssued() {
        Book book = bookWithCopy();
        Reader reader = readerWithTicket();
        backend.books().archive(admin, book.id(), true);
        assertTrue(backend.books().search(employee, "", 20, 0).isEmpty());
        fails(CONFLICT, () -> backend.loans().issue(employee, book.id(), reader.id()));
        backend.books().archive(admin, book.id(), false);
        var copy = backend.books().copies(admin, book.id()).getFirst();
        backend.books().setCopyCondition(admin, copy.id(), CopyCondition.REPAIR);
        fails(CONFLICT, () -> backend.loans().issue(employee, book.id(), reader.id()));
    }

    @Test void ticketReplacementIsAtomicOnDuplicateNumber() {
        Reader first = readerWithTicket();
        Reader second = readerWithTicket();
        String occupiedNumber = backend.readers().tickets(admin, second.id()).getFirst().ticketNumber();
        fails(CONFLICT, () -> backend.readers().issueTicket(admin, first.id(), occupiedNumber,
                LocalDate.now(clock), LocalDate.now(clock).plusDays(10)));
        assertTrue(backend.readers().tickets(admin, first.id()).getFirst().active());
    }

    @Test void concurrentIssuanceHasExactlyOneWinner() throws Exception {
        Book book = bookWithCopy();
        Reader first = readerWithTicket();
        Reader second = readerWithTicket();
        String otherPassword = UUID.randomUUID().toString();
        backend.users().create(admin, "employee2", otherPassword, "Второй сотрудник", Role.USER);
        SessionToken other = backend.auth().login("employee2", otherPassword).token();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> a = executor.submit(() -> attemptIssue(ready, start, employee, book.id(), first.id()));
            Future<Boolean> b = executor.submit(() -> attemptIssue(ready, start, other, book.id(), second.id()));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, (a.get(10, TimeUnit.SECONDS) ? 1 : 0) + (b.get(10, TimeUnit.SECONDS) ? 1 : 0));
        } finally { start.countDown(); }
        assertEquals(1, backend.loans().statistics(admin).activeLoans());
    }

    @Test void databaseRejectsSecondActiveLoanEvenWithoutServiceCheck() throws Exception {
        Book book = bookWithCopy();
        Reader reader = readerWithTicket();
        Loan loan = backend.loans().issue(employee, book.id(), reader.id());
        try (Connection c = connections.open()) {
            SQLException error = assertThrows(SQLException.class, () -> Sql.update(c, """
                    INSERT INTO loans(copy_id,reader_id,issued_by,status_code,issue_date,planned_return_date,daily_fine_rate)
                    VALUES (?,?,?,'active',?,?,?)
                    """, loan.copyId(), reader.id(), employeeUser.id(), loan.issueDate(), loan.plannedReturnDate(), loan.dailyFineRate()));
            assertEquals("23505", error.getSQLState());
        }
    }

    @Test void failedReturnRollsBackDateAndStatus() throws Exception {
        Book book = bookWithCopy();
        Reader reader = readerWithTicket();
        Loan loan = backend.loans().issue(employee, book.id(), reader.id());
        clock.advance(Duration.ofDays(17));
        relogin();
        try (Connection c = connections.open()) {
            Sql.update(c, "INSERT INTO fines(loan_id,amount) VALUES (?,?)", loan.id(), BigDecimal.ONE);
        }
        fails(CONFLICT, () -> backend.loans().returnBook(employee, loan.id()));
        Loan unchanged = backend.loans().history(employee, reader.id(), 20, 0).getFirst();
        assertNull(unchanged.actualReturnDate());
        assertEquals("active", unchanged.statusCode());
        assertEquals(0, backend.books().availableCopies(employee, book.id()));
    }

    private boolean attemptIssue(CountDownLatch ready, CountDownLatch start, SessionToken token, long book, long reader) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Запуск не состоялся.");
        try { backend.loans().issue(token, book, reader); return true; }
        catch (LibraryException exception) { assertEquals(CONFLICT, exception.code()); return false; }
    }
    private Book bookWithCopy() {
        Book book = backend.books().create(admin, draft("Книга"));
        backend.books().addCopy(admin, book.id(), UUID.randomUUID().toString());
        return book;
    }
    private Reader readerWithTicket() {
        Reader reader = backend.readers().create(admin, new ReaderDraft("Читатель", null, null));
        backend.readers().issueTicket(admin, reader.id(), UUID.randomUUID().toString(),
                LocalDate.now(clock), LocalDate.now(clock).plusYears(1));
        return reader;
    }
    private static BookDraft draft(String title) { return new BookDraft(title, null, null, null, null, "", List.of()); }
    private void relogin() {
        admin = backend.auth().login("admin", adminPassword).token();
        employee = backend.auth().login("employee", employeePassword).token();
    }
    private static void fails(LibraryException.Code code, org.junit.jupiter.api.function.Executable operation) {
        assertEquals(code, assertThrows(LibraryException.class, operation).code());
    }
    private static String resource(String name) throws Exception {
        try (var stream = LibraryBackendIT.class.getClassLoader().getResourceAsStream(name)) {
            if (stream == null) throw new IllegalStateException("SQL-ресурс отсутствует.");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
