BEGIN;
SET LOCAL TIME ZONE 'Europe/Moscow';
SELECT pg_advisory_xact_lock(731409826);

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM users u JOIN roles r ON r.id=u.role_id WHERE r.code='admin' AND u.active) THEN
        RAISE EXCEPTION 'Create an active library administrator before loading demo data';
    END IF;
END $$;

CREATE TEMP TABLE demo_books (n integer, title text, author text, year integer, genre text, isbn text) ON COMMIT DROP;
INSERT INTO demo_books(n,title,author,year,genre) VALUES
 (1,'Мастер и Маргарита','Михаил Булгаков',1967,'Роман'),
 (2,'Преступление и наказание','Фёдор Достоевский',1866,'Роман'),
 (3,'Евгений Онегин','Александр Пушкин',1833,'Поэзия'),
 (4,'Отцы и дети','Иван Тургенев',1862,'Роман'),
 (5,'Война и мир','Лев Толстой',1869,'Роман'),
 (6,'Герой нашего времени','Михаил Лермонтов',1840,'Роман'),
 (7,'Мёртвые души','Николай Гоголь',1842,'Роман'),
 (8,'Чайка','Антон Чехов',1896,'Драматургия'),
 (9,'Собачье сердце','Михаил Булгаков',1925,'Повесть'),
 (10,'1984','Джордж Оруэлл',1949,'Фантастика'),
 (11,'Маленький принц','Антуан де Сент-Экзюпери',1943,'Повесть'),
 (12,'451 градус по Фаренгейту','Рэй Брэдбери',1953,'Фантастика'),
 (13,'Пикник на обочине','Аркадий и Борис Стругацкие',1972,'Фантастика'),
 (14,'Понедельник начинается в субботу','Аркадий и Борис Стругацкие',1965,'Фантастика'),
 (15,'Три товарища','Эрих Мария Ремарк',1936,'Роман'),
 (16,'Портрет Дориана Грея','Оскар Уайльд',1890,'Роман');
UPDATE demo_books d SET isbn = base.prefix || ((10 - (
    SELECT sum(substring(base.prefix, i, 1)::integer * CASE WHEN i % 2 = 1 THEN 1 ELSE 3 END)
    FROM generate_series(1,12) AS positions(i)) % 10) % 10)::text
FROM (SELECT n, '978000000' || lpad(n::text,3,'0') AS prefix FROM demo_books) base WHERE d.n=base.n;

INSERT INTO genres(name) SELECT DISTINCT genre FROM demo_books ON CONFLICT(name) DO NOTHING;
INSERT INTO publishers(name) VALUES ('АСТ'),('Азбука'),('Эксмо') ON CONFLICT(name) DO NOTHING;
INSERT INTO authors(name) SELECT DISTINCT d.author FROM demo_books d WHERE NOT EXISTS (SELECT 1 FROM authors a WHERE a.name=d.author);
INSERT INTO books(title,isbn,publication_year,genre_id,publisher_id,description)
SELECT d.title,d.isbn,d.year,g.id,p.id,'Демонстрационная запись для учебного проекта. ISBN условный; год — первая публикация произведения.'
FROM demo_books d JOIN genres g ON g.name=d.genre
JOIN publishers p ON p.name=CASE d.n % 3 WHEN 0 THEN 'АСТ' WHEN 1 THEN 'Азбука' ELSE 'Эксмо' END
ORDER BY d.n ON CONFLICT(isbn) DO NOTHING;
INSERT INTO book_authors(book_id,author_id)
SELECT b.id,min(a.id) FROM demo_books d JOIN books b ON b.isbn=d.isbn JOIN authors a ON a.name=d.author GROUP BY b.id
ON CONFLICT DO NOTHING;
INSERT INTO book_copies(book_id,inventory_number,condition)
SELECT b.id,'DEMO-' || lpad(d.n::text,3,'0') || '-' || copy.n,
       CASE WHEN d.n=7 THEN 'repair' WHEN d.n=8 AND copy.n=3 THEN 'retired' ELSE 'usable' END
FROM demo_books d JOIN books b ON b.isbn=d.isbn CROSS JOIN generate_series(1,3) copy(n)
WHERE d.n<>3 OR copy.n=1
ON CONFLICT(inventory_number) DO NOTHING;

CREATE TEMP TABLE demo_readers(n integer, full_name text) ON COMMIT DROP;
INSERT INTO demo_readers VALUES
 (1,'Иван Петров'),(2,'Мария Соколова'),(3,'Алексей Волков'),(4,'Екатерина Орлова'),
 (5,'Дмитрий Морозов'),(6,'Анна Кузнецова'),(7,'Сергей Лебедев'),(8,'Ольга Новикова');
INSERT INTO readers(full_name,email)
SELECT d.full_name,'reader' || d.n || '@example.test' FROM demo_readers d
WHERE NOT EXISTS (SELECT 1 FROM readers r WHERE r.email='reader' || d.n || '@example.test');
INSERT INTO reader_tickets(reader_id,ticket_number,valid_from,valid_until)
SELECT r.id,'DEMO-TICKET-' || lpad(d.n::text,3,'0'),CURRENT_DATE-365,CURRENT_DATE+365
FROM demo_readers d JOIN readers r ON r.email='reader' || d.n || '@example.test'
WHERE NOT EXISTS (SELECT 1 FROM reader_tickets t WHERE t.reader_id=r.id AND t.active)
ON CONFLICT(ticket_number) DO NOTHING;

CREATE TEMP TABLE demo_loans(book_n integer, reader_n integer, days_ago integer, returned_ago integer) ON COMMIT DROP;
INSERT INTO demo_loans VALUES (1,1,4,NULL),(2,2,22,NULL),(3,3,6,NULL),(4,4,19,NULL),
 (5,5,2,NULL),(6,6,30,NULL),(10,7,25,5),(12,8,24,8);
INSERT INTO loans(copy_id,reader_id,issued_by,returned_by,status_code,issue_date,planned_return_date,actual_return_date,daily_fine_rate)
SELECT copy.id,r.id,employee.id,CASE WHEN d.returned_ago IS NULL THEN NULL ELSE employee.id END,
       CASE WHEN d.returned_ago IS NULL THEN 'active' ELSE 'returned' END,
       CURRENT_DATE-d.days_ago,CURRENT_DATE-d.days_ago+14,
       CASE WHEN d.returned_ago IS NULL THEN NULL ELSE CURRENT_DATE-d.returned_ago END,10.00
FROM demo_loans d JOIN book_copies copy ON copy.inventory_number='DEMO-' || lpad(d.book_n::text,3,'0') || '-1'
JOIN readers r ON r.email='reader' || d.reader_n || '@example.test'
CROSS JOIN LATERAL (SELECT u.id FROM users u JOIN roles role ON role.id=u.role_id WHERE u.active AND role.code='admin' ORDER BY u.id LIMIT 1) employee
WHERE NOT EXISTS (SELECT 1 FROM loans l WHERE l.copy_id=copy.id);
INSERT INTO fines(loan_id,amount,paid_at,paid_by)
SELECT l.id,(l.actual_return_date-l.planned_return_date)*l.daily_fine_rate,
       CASE WHEN r.email='reader8@example.test' THEN CURRENT_TIMESTAMP ELSE NULL END,
       CASE WHEN r.email='reader8@example.test' THEN l.returned_by ELSE NULL END
FROM loans l JOIN readers r ON r.id=l.reader_id JOIN book_copies copy ON copy.id=l.copy_id
WHERE copy.inventory_number IN ('DEMO-010-1','DEMO-012-1') AND l.actual_return_date>l.planned_return_date
ON CONFLICT(loan_id) DO NOTHING;
COMMIT;
