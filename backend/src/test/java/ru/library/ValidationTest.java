package ru.library;

import org.junit.jupiter.api.Test;
import ru.library.security.Validation;
import ru.library.exception.LibraryException;
import static org.junit.jupiter.api.Assertions.*;

class ValidationTest {
    @Test void normalizesLogin() { assertEquals("employee", Validation.login(" Employee ")); }
    @Test void rejectsMalformedLoginAndInvalidPagination() {
        assertThrows(LibraryException.class, () -> Validation.login("' OR 1=1 --"));
        assertThrows(LibraryException.class, () -> Validation.page(201, 0));
        assertThrows(LibraryException.class, () -> Validation.page(20, -1));
    }
    @Test void escapesSearchWildcards() {
        assertEquals("%!%!_!!%", Validation.pattern("%_!"));
        assertEquals("%O'Connor%", Validation.pattern("O'Connor"));
    }
}
