package ru.library.model;

public record Reader(long id, String fullName, String phone, String email, boolean active) { }
