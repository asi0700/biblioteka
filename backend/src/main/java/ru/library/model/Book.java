package ru.library.model;

public record Book(long id, String title, String isbn, Integer publicationYear, Long genreId, Long publisherId, String description, boolean archived, java.util.List<Long> authorIds) {
    public Book { authorIds = java.util.List.copyOf(authorIds); }
 }
