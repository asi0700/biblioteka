package ru.library.model;

public enum Role {
    ADMIN("admin"), USER("user");
    private final String code;
    Role(String code) { this.code = code; }
    public String code() { return code; }
    public static Role fromCode(String code) {
        return switch (code) {
            case "admin" -> ADMIN;
            case "user" -> USER;
            default -> throw new IllegalArgumentException("Неизвестная роль.");
        };
    }
}
