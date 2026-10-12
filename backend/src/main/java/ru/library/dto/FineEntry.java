package ru.library.dto;

import ru.library.model.Fine;

public record FineEntry(Fine fine, long readerId, String readerName, String bookTitle, String inventoryNumber) { }
