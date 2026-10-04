package ru.library.security;

import ru.library.exception.LibraryException;
import java.util.Locale;
import static ru.library.exception.LibraryException.Code.VALIDATION;

public final class Validation {
    private Validation() { }
    public static String text(String value, int max, String field) {
        if (value == null || value.isBlank() || value.strip().length() > max)
            throw new LibraryException(VALIDATION, "Некорректное поле: " + field + ".");
        return value.strip();
    }
    public static String optional(String value, int max, String field) {
        return value == null || value.isBlank() ? null : text(value, max, field);
    }
    public static String login(String login) {
        String value = text(login, 50, "логин").toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9_.-]{3,50}"))
            throw new LibraryException(VALIDATION, "Логин: 3–50 латинских букв, цифр, точек, дефисов или подчёркиваний.");
        return value;
    }
    public static long id(long value) {
        if (value <= 0) throw new LibraryException(VALIDATION, "Идентификатор должен быть положительным.");
        return value;
    }
    public static void page(int limit, int offset) {
        if (limit < 1 || limit > 200 || offset < 0)
            throw new LibraryException(VALIDATION, "Размер страницы: 1–200, смещение неотрицательное.");
    }
    public static String pattern(String text) {
        String value = text == null ? "" : text.strip();
        if (value.length() > 300) throw new LibraryException(VALIDATION, "Слишком длинный поисковый запрос.");
        return "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
    public static <T> T required(T value, String field) {
        if (value == null) throw new LibraryException(VALIDATION, "Не задано поле: " + field + ".");
        return value;
    }
}
