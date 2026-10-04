package ru.library.model;

public record BookCopy(long id, long bookId, String inventoryNumber, CopyCondition condition) { }
