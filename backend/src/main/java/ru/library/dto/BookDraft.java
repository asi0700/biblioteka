package ru.library.dto;

import java.util.List;
public record BookDraft(String title, String isbn, Integer publicationYear,
                        Long genreId, Long publisherId, String description, List<Long> authorIds) {
    public BookDraft { authorIds = authorIds == null ? List.of() : List.copyOf(authorIds); }
}
