package ru.library.dto;

public record LibraryStatistics(long books, long usableCopies, long activeReaders,
                                long activeLoans, long overdueLoans, java.math.BigDecimal unpaidFines) { }
