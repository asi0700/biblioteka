package ru.library.dto;

import ru.library.model.Loan;

public record LoanEntry(Loan loan, String readerName, String bookTitle, String inventoryNumber) { }
