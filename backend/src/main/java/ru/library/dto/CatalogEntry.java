package ru.library.dto;

import ru.library.model.Book;

public record CatalogEntry(Book book, String authors, long available) { }
