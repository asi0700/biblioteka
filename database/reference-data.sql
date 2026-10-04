BEGIN;
INSERT INTO roles(code, name) VALUES ('admin', 'Администратор'), ('user', 'Сотрудник')
ON CONFLICT (code) DO NOTHING;
INSERT INTO loan_statuses(code, name) VALUES ('active', 'Выдана'), ('returned', 'Возвращена')
ON CONFLICT (code) DO NOTHING;
INSERT INTO settings(key, value) VALUES ('loan_days', 14), ('daily_fine_rate', 10.00)
ON CONFLICT (key) DO NOTHING;
COMMIT;
