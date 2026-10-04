package ru.library.model;

public record ReaderTicket(long id, long readerId, String ticketNumber, java.time.LocalDate validFrom, java.time.LocalDate validUntil, boolean active) { }
