package ru.library;

import org.junit.jupiter.api.Test;
import ru.library.security.PasswordHasher;
import ru.library.exception.LibraryException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {
    private final PasswordHasher passwords = new PasswordHasher();

    @Test void hashesAreSaltedAndPasswordsAreVerified() {
        String password = UUID.randomUUID().toString();
        String first = passwords.hash(password);
        String second = passwords.hash(password);
        assertNotEquals(password, first);
        assertNotEquals(first, second);
        assertTrue(first.startsWith("$2a$12$"));
        assertTrue(passwords.verify(password, first));
        assertFalse(passwords.verify(UUID.randomUUID().toString(), first));
    }
    @Test void rejectsTooShortAndTooLongPasswords() {
        assertThrows(LibraryException.class, () -> passwords.hash("a".repeat(9)));
        assertThrows(LibraryException.class, () -> passwords.hash("я".repeat(37)));
        assertFalse(passwords.verify("a".repeat(73), passwords.hash(UUID.randomUUID().toString())));
    }
    @Test void invalidHashDoesNotCrashVerification() {
        assertFalse(passwords.verify(UUID.randomUUID().toString(), "invalid"));
        assertFalse(passwords.verify(null, null));
    }
}
