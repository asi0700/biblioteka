package ru.library.dto;

import java.time.Instant;

public record SystemStatus(String databaseName, String databaseVersion, long responseMillis,
                           long tables, long users, long activeUsers, Instant checkedAt) { }
