BEGIN;

CREATE TABLE roles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE CHECK (code IN ('admin', 'user')),
    name VARCHAR(100) NOT NULL CHECK (btrim(name) <> '')
);

CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    login VARCHAR(50) NOT NULL CHECK (login ~ '^[a-z0-9_.-]{3,50}$'),
    password_hash VARCHAR(60) NOT NULL CHECK (password_hash ~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$'),
    full_name VARCHAR(200) NOT NULL CHECK (btrim(full_name) <> ''),
    role_id BIGINT NOT NULL REFERENCES roles(id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    auth_version BIGINT NOT NULL DEFAULT 0 CHECK (auth_version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_login_unique UNIQUE (login)
);

CREATE TABLE authors (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(200) NOT NULL CHECK (btrim(name) <> '')
);
CREATE TABLE genres (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE CHECK (btrim(name) <> '')
);
CREATE TABLE publishers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(200) NOT NULL UNIQUE CHECK (btrim(name) <> '')
);
CREATE TABLE books (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title VARCHAR(300) NOT NULL CHECK (btrim(title) <> ''),
    isbn VARCHAR(20) UNIQUE,
    publication_year INTEGER CHECK (publication_year BETWEEN 1 AND 9999),
    genre_id BIGINT REFERENCES genres(id),
    publisher_id BIGINT REFERENCES publishers(id),
    description TEXT NOT NULL DEFAULT '',
    archived BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE book_authors (
    book_id BIGINT NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    author_id BIGINT NOT NULL REFERENCES authors(id),
    PRIMARY KEY (book_id, author_id)
);
CREATE TABLE book_copies (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    book_id BIGINT NOT NULL REFERENCES books(id),
    inventory_number VARCHAR(50) NOT NULL UNIQUE CHECK (btrim(inventory_number) <> ''),
    condition VARCHAR(20) NOT NULL DEFAULT 'usable' CHECK (condition IN ('usable', 'repair', 'retired'))
);
CREATE TABLE readers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name VARCHAR(200) NOT NULL CHECK (btrim(full_name) <> ''),
    phone VARCHAR(30),
    email VARCHAR(254),
    active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE reader_tickets (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reader_id BIGINT NOT NULL REFERENCES readers(id),
    ticket_number VARCHAR(50) NOT NULL UNIQUE CHECK (btrim(ticket_number) <> ''),
    valid_from DATE NOT NULL,
    valid_until DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (valid_until >= valid_from)
);
CREATE TABLE loan_statuses (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE CHECK (code IN ('active', 'returned')),
    name VARCHAR(100) NOT NULL CHECK (btrim(name) <> '')
);
CREATE TABLE loans (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    copy_id BIGINT NOT NULL REFERENCES book_copies(id),
    reader_id BIGINT NOT NULL REFERENCES readers(id),
    issued_by BIGINT NOT NULL REFERENCES users(id),
    returned_by BIGINT REFERENCES users(id),
    status_code VARCHAR(20) NOT NULL REFERENCES loan_statuses(code),
    issue_date DATE NOT NULL,
    planned_return_date DATE NOT NULL,
    actual_return_date DATE,
    daily_fine_rate NUMERIC(10,2) NOT NULL CHECK (daily_fine_rate >= 0),
    CHECK (planned_return_date >= issue_date),
    CHECK (actual_return_date IS NULL OR actual_return_date >= issue_date),
    CHECK ((status_code = 'active' AND actual_return_date IS NULL AND returned_by IS NULL)
        OR (status_code = 'returned' AND actual_return_date IS NOT NULL AND returned_by IS NOT NULL))
);
CREATE TABLE fines (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    loan_id BIGINT NOT NULL UNIQUE REFERENCES loans(id),
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    paid_at TIMESTAMPTZ,
    paid_by BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((paid_at IS NULL AND paid_by IS NULL) OR (paid_at IS NOT NULL AND paid_by IS NOT NULL))
);
CREATE TABLE settings (
    key VARCHAR(50) PRIMARY KEY CHECK (key IN ('loan_days', 'daily_fine_rate')),
    value NUMERIC(10,2) NOT NULL,
    CHECK ((key = 'loan_days' AND value BETWEEN 1 AND 365 AND value = trunc(value))
        OR (key = 'daily_fine_rate' AND value BETWEEN 0 AND 10000))
);


CREATE UNIQUE INDEX one_active_loan_per_copy ON loans(copy_id) WHERE actual_return_date IS NULL;
CREATE UNIQUE INDEX one_active_ticket_per_reader ON reader_tickets(reader_id) WHERE active;
CREATE INDEX book_authors_author_idx ON book_authors(author_id);
CREATE INDEX books_genre_idx ON books(genre_id);
CREATE INDEX books_publisher_idx ON books(publisher_id);
CREATE INDEX book_copies_book_idx ON book_copies(book_id);
CREATE INDEX reader_tickets_reader_idx ON reader_tickets(reader_id);
CREATE INDEX loans_reader_idx ON loans(reader_id);
CREATE INDEX loans_overdue_idx ON loans(planned_return_date) WHERE actual_return_date IS NULL;
CREATE INDEX loans_issued_by_idx ON loans(issued_by);
CREATE INDEX loans_returned_by_idx ON loans(returned_by);
CREATE INDEX users_role_idx ON users(role_id);
COMMIT;
