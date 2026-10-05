# Информационная система автоматизации библиотеки

Учебный командный проект на Java 21 и PostgreSQL. Backend обеспечивает авторизацию,
каталог, учёт читателей, выдачу и возврат книг. Frontend — настольный интерфейс JavaFX.

## Структура

- `backend` — Maven-модуль: модели, JDBC repositories, сервисы и тесты.
- `frontend` — JavaFX: вход, каталог, читатели, выдачи, штрафы и администрирование.
- `database` — схема из 14 таблиц и начальные справочники.
- `docs` — архитектура, подключение интерфейса и проверка проекта.

Корневой `pom.xml` объединяет оба модуля.
Frontend вызывает сервисы backend и не выполняет SQL.

## Запуск из IDE

1. Откройте корневой `pom.xml` как Maven-проект и выберите JDK 21.
2. Создайте конфигурацию **Application**:
   - Main class: `ru.library.frontend.Launcher`.
   - Classpath/module: `library-frontend`.
   - Working directory: корень проекта.
3. В **Environment variables** укажите `LIBRARY_DB_URL`, `LIBRARY_DB_USER`,
   `LIBRARY_DB_PASSWORD` и при необходимости `LIBRARY_ZONE`.
   Для уже настроенной локальной базы перенесите значения из `.env`.
   Java автоматически этот файл не читает.
4. Убедитесь, что служба PostgreSQL запущена, и нажмите **Run**.

Существующую базу повторно создавать или заполнять не нужно.
Для нового компьютера настройте базу и первого администратора по инструкции ниже.
PostgreSQL работает отдельной службой и не останавливается при закрытии приложения.

## База данных

Создайте отдельную БД `library` и отдельного пользователя PostgreSQL.
Из корня проекта выполните, подставив имя пользователя:

```text
psql -h localhost -U <пользователь> -d library -v ON_ERROR_STOP=1 -f database/schema.sql
psql -h localhost -U <пользователь> -d library -v ON_ERROR_STOP=1 -f database/reference-data.sql
```

Схема предназначена для новой пустой БД. Повторное выполнение схемы завершится
ошибкой без удаления существующих данных. Начальные справочники можно применять повторно.

## Настройки

Задайте переменные окружения `LIBRARY_DB_URL`, `LIBRARY_DB_USER`,
`LIBRARY_DB_PASSWORD`. `LIBRARY_ZONE` задаёт часовой пояс дат выдачи и возврата;
по умолчанию используется Europe/Moscow. Параметры задаются в конфигурации запуска IDE.
Локальный `.env` исключён из Git и служит для хранения настроек подключения.

## Сборка

Требуются JDK 21 или новее и Maven 3.9+:

```text
mvn clean verify
```

Для интеграционных тестов требуется запущенный Docker с Linux-контейнерами.
Unit-тесты можно выполнить отдельно: `mvn test`.
Проверка на отдельном PostgreSQL без Docker описана в `docs/testing.md`.
Параметры `LIBRARY_TEST_DB_*` относятся только к тестам и не заменяют
параметры `LIBRARY_DB_*` рабочего приложения.

## Первое включение

После создания БД и задания переменных окружения соберите backend и скопируйте
его зависимости:

```text
mvn -pl backend package -DskipTests
mvn -pl backend org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies -DincludeScope=runtime
```

В обычном терминале Windows из корня проекта запустите:

```text
java -cp "backend/target/classes;backend/target/dependency/*" ru.library.bootstrap.AdminBootstrap
```

Программа запрашивает логин, ФИО и пароль.
Первичная настройка доступна только пока таблица users пуста.
Все последующие учётные записи создаёт администратор через UserService.

Для подключения интерфейса используйте `Backend.fromEnvironment()`;
методы и права описаны в `docs/backend-api.md`.
Схема и ограничения описаны в `docs/database.md`.

## Запуск интерфейса

После настройки PostgreSQL, переменных окружения и первого администратора:

```text
mvn install -DskipTests
mvn -pl frontend javafx:run
```

Вход выполняется учётной записью сотрудника из backend.
Для демонстрации можно загрузить `database/demo-data.sql` через SQL-консоль IDE
или psql. Сценарий показа: [docs/demo.md](docs/demo.md).

Описание экранов, ограничений API и проверок: [docs/frontend.md](docs/frontend.md).

```text
mvn "-Dmaven.repo.local=E:/biblioteka/.local/m2" "-Djava.io.tmpdir=E:/biblioteka/.local/tmp" verify
```
