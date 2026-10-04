package ru.library.model;

public record User(long id, String login, String fullName, Role role, boolean active) { }
