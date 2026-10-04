# Подключение интерфейса

Создайте один экземпляр `Backend.fromEnvironment()` на процесс приложения.
После входа храните `SessionToken` и передавайте его в методы сервисов.
При перезапуске приложения требуется новый вход. Срок сеанса — 8 часов.

```java
Backend backend = Backend.fromEnvironment();
AuthResult result = backend.auth().login(login, password);
SessionToken token = result.token();
List<Book> books = backend.books().search(token, query, 50, 0);
Loan loan = backend.loans().issue(token, bookId, readerId);
Loan returned = backend.loans().returnBook(token, loan.id());
backend.auth().logout(token);
```

Вызовы блокирующие: подключение к БД и BCrypt занимают время.
Frontend должен выполнять их вне потока интерфейса.
Frontend использует пакеты `model`, `dto`, `service` и класс `Backend`.
Repositories и настройки подключения относятся к backend.

Модуль интерфейса добавляется в modules корневого pom.xml и подключает backend:

```xml
<dependency>
    <groupId>ru.library</groupId>
    <artifactId>library-backend</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

При сборке обоих модулей из корня зависимость разрешается в одной сборке.
Для отдельной сборки интерфейса сначала выполните `mvn install` из корня.
Для объектов BookDraft, ReaderDraft и моделей не нужны зависимости JavaFX.

## Сервисы

| Сервис | Основные методы | Права |
| --- | --- | --- |
| auth | login, currentUser, logout | Вход и управление своим сеансом |
| books | search, get, copies, availableCopies | admin/user |
| books | create, update, archive, addCopy, setCopyCondition | admin |
| readers | search, get, tickets | admin/user |
| readers | create, update, setActive, issueTicket | admin |
| loans | issue, returnBook, history, overdue, statistics, statuses | admin/user |
| users | list, roles, create, resetPassword, setAccess | admin |
| users | changeOwnPassword | admin/user |
| dictionaries | list | admin/user |
| dictionaries | create, update, delete | admin |
| settings | get | admin/user |
| settings | update | admin |
| fines | list | admin/user |
| fines | pay | admin |

Две роли фиксированы; администратор назначает их пользователям.
Статусы выдач также фиксированы. Создание новых ролей и статусов не входит в MVP.
Поиск каталога учитывает название, ISBN и имена авторов. Символы % и _ ищутся буквально.
Методы поиска и истории принимают limit (1–200) и offset (от 0).
Списки пользователей и справочников ограничены 500 записями в MVP.
Читательские билеты действуют включительно по обеим датам; новый билет отключает прежний.
Возврат возможен и для отключённого читателя.

Профили пользователей не содержат хешей паролей. После изменения пароля, роли или
активности старые сеансы отклоняются при следующем обращении.
Последнего активного администратора нельзя отключить или перевести в user.

## Ошибки

Сервисы выбрасывают `LibraryException` с кодом:

- VALIDATION — некорректные данные;
- UNAUTHENTICATED — неверные данные входа либо недействительный сеанс;
- FORBIDDEN — недостаточно прав;
- NOT_FOUND — запись не найдена;
- CONFLICT — операция противоречит данным или произошёл конкурентный конфликт;
- STORAGE — недоступна БД или произошла ошибка доступа к данным.

Интерфейс показывает пользователю message; SQL, stack trace и cause не выводятся.
При CONFLICT обновите данные; для выдачи можно повторить действие после обновления.
Нельзя записывать в журналы пароли, параметры подключения и токены сеансов.
