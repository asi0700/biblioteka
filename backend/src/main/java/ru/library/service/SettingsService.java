package ru.library.service;

import ru.library.config.Database;
import ru.library.dto.*;
import ru.library.repository.SettingsRepository;

public final class SettingsService {
    private final Database database;
    private final AuthService auth;
    private final SettingsRepository settings;
    public SettingsService(Database database, AuthService auth, SettingsRepository settings) {
        this.database = database; this.auth = auth; this.settings = settings;
    }
    public LoanSettings get(SessionToken token) {
        return database.transaction(c -> { auth.require(c, token, false); return settings.get(c); });
    }
    public void update(SessionToken token, LoanSettings value) {
        LoanSettings clean = LoanPolicy.validate(value);
        database.transaction(c -> { auth.require(c, token, true); settings.set(c, clean); return null; });
    }
}
