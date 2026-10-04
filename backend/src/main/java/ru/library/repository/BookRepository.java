package ru.library.repository;

import ru.library.dto.BookDraft;
import ru.library.model.*;
import java.sql.*;
import java.util.*;

public final class BookRepository {
    private static final String SELECT = """
            SELECT b.*, ARRAY(SELECT ba.author_id FROM book_authors ba WHERE ba.book_id=b.id ORDER BY ba.author_id) AS author_ids
            FROM books b
            """;

    private Book map(ResultSet result) throws SQLException {
        java.sql.Array array = result.getArray("author_ids");
        List<Long> authors = new ArrayList<>();
        try { for (Object id : (Object[]) array.getArray()) authors.add(((Number) id).longValue()); }
        finally { array.free(); }
        return new Book(result.getLong("id"), result.getString("title"), result.getString("isbn"),
                result.getObject("publication_year", Integer.class), Sql.nullableLong(result, "genre_id"),
                Sql.nullableLong(result, "publisher_id"), result.getString("description"), result.getBoolean("archived"), authors);
    }

    public Optional<Book> find(Connection connection, long id, boolean lock) throws SQLException {
        return Sql.one(connection, SELECT + (lock ? "WHERE b.id=? FOR UPDATE OF b" : "WHERE b.id=?"), this::map, id);
    }

    public List<Book> search(Connection connection, String pattern, int limit, int offset) throws SQLException {
        return Sql.list(connection, SELECT + """
                WHERE NOT b.archived AND (b.title ILIKE ? ESCAPE '!' OR b.isbn ILIKE ? ESCAPE '!'
                  OR EXISTS (SELECT 1 FROM book_authors ba JOIN authors a ON a.id=ba.author_id
                             WHERE ba.book_id=b.id AND a.name ILIKE ? ESCAPE '!'))
                ORDER BY b.id LIMIT ? OFFSET ?
                """, this::map, pattern, pattern, pattern, limit, offset);
    }

    public long create(Connection connection, BookDraft draft) throws SQLException {
        long id = Sql.insertId(connection, """
                INSERT INTO books(title,isbn,publication_year,genre_id,publisher_id,description)
                VALUES (?,?,?,?,?,?) RETURNING id
                """, draft.title(), draft.isbn(), draft.publicationYear(), draft.genreId(), draft.publisherId(), draft.description());
        authors(connection, id, draft.authorIds());
        return id;
    }

    public void edit(Connection connection, long id, BookDraft draft) throws SQLException {
        Sql.update(connection, "UPDATE books SET title=?,isbn=?,publication_year=?,genre_id=?,publisher_id=?,description=? WHERE id=?",
                draft.title(), draft.isbn(), draft.publicationYear(), draft.genreId(), draft.publisherId(), draft.description(), id);
        authors(connection, id, draft.authorIds());
    }

    private void authors(Connection connection, long id, List<Long> authors) throws SQLException {
        Sql.update(connection, "DELETE FROM book_authors WHERE book_id=?", id);
        for (long authorId : authors) Sql.update(connection, "INSERT INTO book_authors(book_id,author_id) VALUES (?,?)", id, authorId);
    }

    public void archive(Connection connection, long id, boolean archived) throws SQLException {
        Sql.update(connection, "UPDATE books SET archived=? WHERE id=?", archived, id);
    }

    public BookCopy copy(ResultSet result) throws SQLException {
        return new BookCopy(result.getLong("id"), result.getLong("book_id"), result.getString("inventory_number"),
                CopyCondition.fromCode(result.getString("condition")));
    }

    public List<BookCopy> copies(Connection connection, long bookId) throws SQLException {
        return Sql.list(connection, "SELECT * FROM book_copies WHERE book_id=? ORDER BY id", this::copy, bookId);
    }

    public Optional<BookCopy> copyById(Connection connection, long id, boolean lock) throws SQLException {
        return Sql.one(connection, lock ? "SELECT * FROM book_copies WHERE id=? FOR UPDATE" : "SELECT * FROM book_copies WHERE id=?", this::copy, id);
    }

    public long addCopy(Connection connection, long bookId, String number) throws SQLException {
        return Sql.insertId(connection, "INSERT INTO book_copies(book_id,inventory_number) VALUES (?,?) RETURNING id", bookId, number);
    }

    public void condition(Connection connection, long copyId, CopyCondition condition) throws SQLException {
        Sql.update(connection, "UPDATE book_copies SET condition=? WHERE id=?", condition.code(), copyId);
    }

    public long available(Connection connection, long bookId) throws SQLException {
        return Sql.one(connection, """
                SELECT count(*) FROM book_copies c JOIN books b ON b.id=c.book_id
                WHERE c.book_id=? AND c.condition='usable' AND NOT b.archived
                AND NOT EXISTS (SELECT 1 FROM loans l WHERE l.copy_id=c.id AND l.actual_return_date IS NULL)
                """, result -> result.getLong(1), bookId).orElseThrow();
    }

    public Optional<BookCopy> freeCopy(Connection connection, long bookId) throws SQLException {
        return Sql.one(connection, """
                SELECT c.* FROM book_copies c WHERE c.book_id=? AND c.condition='usable'
                AND NOT EXISTS (SELECT 1 FROM loans l WHERE l.copy_id=c.id AND l.actual_return_date IS NULL)
                ORDER BY c.id LIMIT 1 FOR UPDATE OF c SKIP LOCKED
                """, this::copy, bookId);
    }
}
