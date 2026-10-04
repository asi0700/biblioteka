package ru.library.exception;

public class LibraryException extends RuntimeException {
    public enum Code { VALIDATION, UNAUTHENTICATED, FORBIDDEN, NOT_FOUND, CONFLICT, STORAGE }
    private final Code code;

    public LibraryException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public LibraryException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() { return code; }
}
