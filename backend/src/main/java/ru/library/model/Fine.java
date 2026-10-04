package ru.library.model;

public record Fine(long id, long loanId, java.math.BigDecimal amount, java.time.Instant paidAt, Long paidBy) { }
