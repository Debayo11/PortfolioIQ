-- Runtime settings an administrator can change without redeploying. Secrets are never stored here.
CREATE TABLE app_settings (
    setting_key   VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(500) NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by    BIGINT REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_audit_action ON audit_logs(action);
