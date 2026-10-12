package ru.library.service;

import ru.library.config.Database;
import ru.library.dto.SessionToken;
import ru.library.dto.SystemStatus;
import ru.library.repository.Sql;
import java.time.Clock;

public final class SystemService {
    private final Database database;
    private final AuthService auth;
    private final Clock clock;

    public SystemService(Database database, AuthService auth, Clock clock) {
        this.database = database; this.auth = auth; this.clock = clock;
    }

    public SystemStatus status(SessionToken token) {
        long start = System.nanoTime();
        return database.transaction(c -> {
            auth.require(c, token, true);
            return Sql.one(c, """
                    SELECT current_database() AS database_name, current_setting('server_version') AS version,
                      (SELECT count(*) FROM information_schema.tables WHERE table_schema=current_schema()) AS tables,
                      (SELECT count(*) FROM users) AS users,
                      (SELECT count(*) FROM users WHERE active) AS active_users
                    """, r -> new SystemStatus(r.getString("database_name"), r.getString("version"),
                    (System.nanoTime() - start) / 1_000_000, r.getLong("tables"), r.getLong("users"),
                    r.getLong("active_users"), clock.instant())).orElseThrow();
        });
    }
}
