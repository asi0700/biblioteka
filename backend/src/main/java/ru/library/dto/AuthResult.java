package ru.library.dto;

import ru.library.model.User;
public record AuthResult(SessionToken token, User user) { }
