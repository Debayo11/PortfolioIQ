package com.portfolioiq.settings;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "app_settings")
public class AppSetting {
    @Id
    @Column(name = "setting_key", length = 100)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 500)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by")
    private Long updatedBy;

    protected AppSetting() {}

    public AppSetting(String key) {
        this.key = key;
        this.value = "";
    }

    public void update(String value, Long userId, Instant when) {
        this.value = value;
        this.updatedBy = userId;
        this.updatedAt = when;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
}
