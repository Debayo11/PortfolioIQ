-- PortfolioIQ initial schema. Monetary values use NUMERIC, never floating point.

CREATE TABLE roles (
    id   SMALLSERIAL PRIMARY KEY,
    name VARCHAR(30) NOT NULL UNIQUE
);
INSERT INTO roles (name) VALUES ('ADMINISTRATOR'), ('INVESTOR'), ('ANALYST');

CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));

CREATE TABLE user_roles (
    user_id BIGINT   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id SMALLINT NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

-- Email verification and password reset (only a hash of the token is stored)
CREATE TABLE user_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    type       VARCHAR(30) NOT NULL CHECK (type IN ('EMAIL_VERIFICATION','PASSWORD_RESET')),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ
);
CREATE INDEX idx_user_tokens_user ON user_tokens(user_id);

CREATE TABLE portfolios (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name          VARCHAR(120) NOT NULL,
    description   VARCHAR(500),
    base_currency CHAR(3) NOT NULL DEFAULT 'USD',
    category      VARCHAR(50),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, name)
);

CREATE TABLE assets (
    id         BIGSERIAL PRIMARY KEY,
    symbol     VARCHAR(20) NOT NULL UNIQUE,
    name       VARCHAR(200) NOT NULL,
    asset_type VARCHAR(30) NOT NULL DEFAULT 'STOCK',
    exchange   VARCHAR(30),
    currency   CHAR(3) NOT NULL DEFAULT 'USD'
);

CREATE TABLE transactions (
    id           BIGSERIAL PRIMARY KEY,
    portfolio_id BIGINT NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    asset_id     BIGINT REFERENCES assets(id),
    type         VARCHAR(20) NOT NULL
                 CHECK (type IN ('BUY','SELL','DIVIDEND','DEPOSIT','WITHDRAWAL','FEE')),
    quantity     NUMERIC(24,8) NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    unit_price   NUMERIC(24,8) NOT NULL DEFAULT 0 CHECK (unit_price >= 0),
    fee          NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (fee >= 0),
    total_amount NUMERIC(19,4) NOT NULL,
    executed_at  TIMESTAMPTZ NOT NULL,
    notes        VARCHAR(500),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- trades and dividends must reference an asset
    CHECK (type IN ('DEPOSIT','WITHDRAWAL','FEE') OR asset_id IS NOT NULL)
);
CREATE INDEX idx_tx_portfolio_date ON transactions(portfolio_id, executed_at);
CREATE INDEX idx_tx_asset ON transactions(asset_id);

CREATE TABLE dividends (
    id             BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL UNIQUE REFERENCES transactions(id) ON DELETE CASCADE,
    portfolio_id   BIGINT NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    asset_id       BIGINT NOT NULL REFERENCES assets(id),
    amount         NUMERIC(19,4) NOT NULL CHECK (amount >= 0),
    ex_date        DATE,
    pay_date       DATE NOT NULL
);
CREATE INDEX idx_dividends_portfolio ON dividends(portfolio_id, pay_date);

CREATE TABLE market_prices (
    id         BIGSERIAL PRIMARY KEY,
    asset_id   BIGINT NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    priced_at  TIMESTAMPTZ NOT NULL,
    price      NUMERIC(24,8) NOT NULL CHECK (price >= 0),
    volume     BIGINT,
    market_cap NUMERIC(24,2),
    source     VARCHAR(50) NOT NULL,
    simulated  BOOLEAN NOT NULL DEFAULT FALSE,   -- clearly flags development data
    UNIQUE (asset_id, priced_at)
);
CREATE INDEX idx_prices_asset_time ON market_prices(asset_id, priced_at DESC);

CREATE TABLE watchlists (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, name)
);

CREATE TABLE watchlist_items (
    watchlist_id BIGINT NOT NULL REFERENCES watchlists(id) ON DELETE CASCADE,
    asset_id     BIGINT NOT NULL REFERENCES assets(id),
    added_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (watchlist_id, asset_id)
);

CREATE TABLE price_alerts (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    asset_id          BIGINT NOT NULL REFERENCES assets(id),
    condition_type    VARCHAR(20) NOT NULL CHECK (condition_type IN ('ABOVE','BELOW','PERCENT_MOVE')),
    target_price      NUMERIC(24,8),
    threshold_percent NUMERIC(8,4),
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    triggered_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((condition_type = 'PERCENT_MOVE' AND threshold_percent IS NOT NULL)
        OR (condition_type <> 'PERCENT_MOVE' AND target_price IS NOT NULL))
);
CREATE INDEX idx_alerts_active ON price_alerts(asset_id) WHERE active;

CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(40) NOT NULL,
    title      VARCHAR(200) NOT NULL,
    message    VARCHAR(1000) NOT NULL,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user ON notifications(user_id, created_at DESC);

CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id) ON DELETE SET NULL,
    action      VARCHAR(80) NOT NULL,
    entity_type VARCHAR(50),
    entity_id   VARCHAR(50),
    details     VARCHAR(1000),
    ip_address  VARCHAR(45),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_created ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_user ON audit_logs(user_id);
