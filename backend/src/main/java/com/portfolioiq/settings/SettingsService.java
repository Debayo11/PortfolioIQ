package com.portfolioiq.settings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** Small typed key-value store. A missing or unreadable value falls back to the caller's default. */
@Service
public class SettingsService {
    private final AppSettingRepository repo;
    private final Clock clock;

    public SettingsService(AppSettingRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean getBoolean(String key, boolean defaultValue) {
        return repo.findById(key).map(s -> Boolean.parseBoolean(s.getValue().trim())).orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public int getInt(String key, int defaultValue) {
        return repo.findById(key).map(s -> {
            try { return Integer.parseInt(s.getValue().trim()); } catch (NumberFormatException e) { return defaultValue; }
        }).orElse(defaultValue);
    }

    @Transactional
    public void set(String key, String value, Long userId) {
        AppSetting s = repo.findById(key).orElseGet(() -> new AppSetting(key));
        s.update(value, userId, clock.instant());
        repo.save(s);
    }
}
