package ru.library.security;

import org.mindrot.jbcrypt.BCrypt;
import ru.library.exception.LibraryException;
import java.nio.charset.StandardCharsets;
import static ru.library.exception.LibraryException.Code.VALIDATION;

public final class PasswordHasher {
    public String hash(String password) {
        if (password == null || password.length() < 10 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new LibraryException(VALIDATION, "Пароль: минимум 10 символов и максимум 72 байта UTF-8.");
        return BCrypt.hashpw(password, BCrypt.gensalt(12));
    }
    public boolean verify(String password, String hash) {
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72 || hash == null) return false;
        try { return BCrypt.checkpw(password, hash); }
        catch (IllegalArgumentException exception) { return false; }
    }
}
