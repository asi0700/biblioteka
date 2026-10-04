package ru.library.dto;

import java.math.BigDecimal;
public record LoanSettings(int loanDays, BigDecimal dailyFineRate) { }
