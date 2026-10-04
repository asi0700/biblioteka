package ru.library.model;

public record Loan(long id, long copyId, long readerId, long issuedBy, Long returnedBy, String statusCode, java.time.LocalDate issueDate, java.time.LocalDate plannedReturnDate, java.time.LocalDate actualReturnDate, java.math.BigDecimal dailyFineRate) { }
