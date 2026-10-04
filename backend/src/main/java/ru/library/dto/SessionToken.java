package ru.library.dto;

public record SessionToken(String value) {
    @Override public String toString() { return "SessionToken[скрыт]"; }
}
