# PortfolioIQ

Investment and portfolio management platform: Spring Boot + PostgreSQL backend, React/TypeScript frontend (in progress).

> **Status: Stage 3 of 6.** Built incrementally, with each stage verified before the next. Nothing listed as "done" below is a stub.

## Roadmap

| Stage | Scope | Status |
|------:|-------|--------|
| 1 | Architecture, PostgreSQL schema (Flyway), Docker/Compose, JWT auth + roles, financial calculation core + tests | **Done** |
| 2 | Portfolios, assets, transactions API, ownership checks, audit logging, email verification and password recovery | **Done** |
| 3 | Market data (real API if key set, otherwise labelled simulated data), watchlists, price alerts, notifications, Redis caching, rate limiting, asset transfers between portfolios | **Done** |
| 4 | Analytics engine: portfolio value history, ROI, volatility, max drawdown, diversification, benchmark comparison | Next |
| 5 | React + TypeScript + Tailwind + Recharts frontend, admin panel | Planned |
| 6 | Integration tests (Testcontainers), production config, CI | Planned |

## What works so far

**Stage 1**
- BCrypt (cost 12) hashing, stateless JWT (HS256), roles `ADMINISTRATOR`, `INVESTOR`, `ANALYST`
- Self-registration always yields `INVESTOR`; `/api/admin/**` requires `ADMINISTRATOR`
- Consistent JSON errors, no internals leaked; Swagger UI at `/swagger-ui.html`; health at `/actuator/health`
- `PositionCalculator`: average cost, realized/unrealized P&L, dividends, fees (`BigDecimal` only)

**Stage 2**
- Portfolios: create, list, view, update, delete. Every query is scoped to the signed-in user, so another user's portfolio returns 404
- Assets: shared catalogue with search; only `ADMINISTRATOR`/`ANALYST` can add assets
- Transactions: BUY, SELL, DIVIDEND, DEPOSIT, WITHDRAWAL, FEE with validation, decimal-precision limits, paging, filtering (type, asset, date range) and sort whitelist
- Selling more than held is rejected (HTTP 422), including back-dated sales. The portfolio row is locked during writes so concurrent requests cannot double-sell
- Deleting a transaction is refused (HTTP 409) if later sales depend on it
- Holdings and a cost-based portfolio summary (invested cost, realized P&L, dividends, fees, net deposits)
- Audit log of registrations, logins, portfolio/asset/transaction changes, password resets; viewable by admins at `GET /api/admin/audit-logs`
- Email verification and password recovery with one-time tokens (only SHA-256 hashes stored; 24 h / 1 h expiry)

**Stage 3**
- Market data: real quotes from Finnhub when `MARKET_DATA_API_KEY` is set, otherwise deterministic data that is flagged `simulated: true` in every response. `GET /api/market/status` tells clients which one is active
- Quotes (price, daily change and %, volume and market cap where the source provides them) and price history with the 24H / 7D / 30D / 3M / 6M / 1Y / ALL filters
- Redis quote cache (60 s default) and a background refresh job guarded by a Redis lock; both fail open if Redis is down
- Watchlists, price alerts (above, below, % move vs previous close) and an in-app notification centre. Notifications are also created for recorded transactions, dividends and transfers
- Holdings and portfolio summary now include market value, unrealized P&L, total return and today's change, with simulated/stale/unpriced flags
- Asset transfers between your portfolios that carry cost basis and realize no profit or loss; undo is available while later sales do not depend on the shares
- Rate limiting in Redis: 10 requests/min on sign-in, registration and password endpoints, 120 requests/min elsewhere, per client address

## Known limitations (deliberate, to be addressed in later stages)

- Real market data comes from one provider (Finnhub). Its free tier allows about 60 calls a minute, so the refresh job caps each cycle at 50 assets. It does not return volume or market cap, so those fields are empty rather than invented
- Real price history builds up from the prices this app stores after you start it (the provider's candle data is a paid feature). Simulated history is generated on demand
- Alerts fire once, then deactivate. Recreate an alert to keep watching
- Portfolio value over time and comparisons are Stage 4. No projections are produced anywhere
- No currency conversion: record prices in the portfolio's base currency; transfers require matching base currencies
- Cash balance is not enforced: withdrawals and buys are not checked against deposits
- Transactions cannot be edited, only deleted and re-entered. Transfers are undone as a pair
- Existing JWTs stay valid until they expire after a password reset (default 60 minutes)
- Rate limiting uses the connection address. Behind a reverse proxy, configure the proxy and Spring's forwarded-header handling so real client addresses are used
- The frontend routes the emailed links point to (`/verify-email`, `/reset-password`) arrive in Stage 5; until then POST the token to the API
- Backend automated tests are unit tests. Integration tests against a real PostgreSQL and Redis arrive in Stage 6

## Architecture

```
React SPA (Stage 5)  --REST/JSON + Bearer JWT-->  Spring Boot API  -->  PostgreSQL (Flyway)
                                                        |
                                                        +--> Redis (cache, Stage 3)
```

Backend layers: `controller -> service -> repository -> entity`, with DTOs at the edge and `finance` as a framework-free calculation core so money logic is unit-testable in isolation.

## Project structure

```
portfolioiq/
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/portfolioiq/
│       │   ├── PortfolioIqApplication.java
│       │   ├── auth/        controller, service, tokens, email
│       │   ├── audit/       audit log entity, service, admin endpoint
│       │   ├── asset/       asset catalogue
│       │   ├── portfolio/   portfolios, holdings, summary
│       │   ├── transaction/ transactions, dividends, transfers
│       │   ├── market/      providers (simulated, Finnhub), quote cache, refresh job, API
│       │   ├── alert/       price alerts and rules
│       │   ├── notification/ in-app notification centre
│       │   ├── watchlist/   watchlists
│       │   ├── config/      SecurityConfig, OpenApiConfig
│       │   ├── security/    JwtService, JwtAuthenticationFilter, UserDetailsService
│       │   ├── user/        User, Role, repositories
│       │   ├── common/      ApiError, ApiException, GlobalExceptionHandler
│       │   └── finance/     PositionCalculator, TransactionRules (framework-free)
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/migration/V1__init_schema.sql
│       └── test/java/com/portfolioiq/  finance/ and transaction/ unit tests
├── docs/ARCHITECTURE.md
├── docker-compose.yml
├── .env.example
└── README.md
```

## Run it

Prerequisites: Docker + Docker Compose (or Java 21, Maven 3.9+, PostgreSQL 16, Redis 7 locally).

```bash
cp .env.example .env          # then edit: set a strong POSTGRES_PASSWORD and JWT_SECRET
docker compose up --build
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

Without Docker:

```bash
cd backend
export DB_URL=jdbc:postgresql://localhost:5432/portfolioiq DB_USER=portfolioiq DB_PASSWORD=... JWT_SECRET=...
mvn spring-boot:run
```

Run the tests:

```bash
cd backend && mvn test
```

## API (Stage 2)

| Method | Path | Notes |
|---|---|---|
| POST | `/api/auth/register`, `/login` | public |
| GET | `/api/auth/me` | |
| POST | `/api/auth/verify-email` | body `{"token":"..."}` |
| POST | `/api/auth/resend-verification` | signed in |
| POST | `/api/auth/forgot-password` | always 202, never reveals whether the account exists |
| POST | `/api/auth/reset-password` | body `{"token","newPassword"}` |
| GET/POST | `/api/portfolios` | list / create |
| GET/PUT/DELETE | `/api/portfolios/{id}` | |
| GET | `/api/portfolios/{id}/summary`, `/holdings` | |
| GET/POST | `/api/portfolios/{id}/transactions` | filters: `type`, `assetId`, `from`, `to`, `page`, `size`, `sort` |
| DELETE | `/api/portfolios/{id}/transactions/{txId}` | |
| GET | `/api/assets?q=` | search |
| POST | `/api/assets` | admin or analyst |
| GET | `/api/admin/audit-logs` | admin only |

| GET | `/api/market/status` | which data source is active (real or simulated) |
| GET | `/api/market/quotes/{assetId}`, `/api/market/quotes?assetIds=1,2` | latest quotes (max 50 per batch) |
| GET | `/api/market/history/{assetId}?range=` | `24H`, `7D`, `30D`, `3M`, `6M`, `1Y`, `ALL` |
| GET/POST | `/api/watchlists` | list / create |
| GET/PUT/DELETE | `/api/watchlists/{id}` | |
| POST/DELETE | `/api/watchlists/{id}/items`, `/items/{assetId}` | add / remove an asset |
| GET/POST | `/api/alerts` | list / create (`ABOVE`, `BELOW`, `PERCENT_MOVE`) |
| DELETE | `/api/alerts/{id}` | |
| GET | `/api/notifications?unreadOnly=`, `/api/notifications/unread-count` | |
| POST | `/api/notifications/{id}/read`, `/api/notifications/read-all` | |
| POST | `/api/transfers` | move shares between your portfolios |
| DELETE | `/api/transfers/{transferId}` | undo a transfer |

Full request/response schemas are in Swagger UI.

## Configuration

Set in `.env` (see `.env.example`). Secrets are never committed.

| Variable | Purpose | Default |
|---|---|---|
| `MARKET_DATA_API_KEY` | Finnhub key; empty means simulated data | empty |
| `MARKET_REFRESH_INTERVAL_MS` | how often tracked assets are refreshed | 300000 |
| `MARKET_MAX_REQUESTS_PER_REFRESH` | cap per cycle (respects free-tier limits) | 50 |
| `MARKET_CACHE_TTL_SECONDS` | Redis quote cache lifetime | 60 |
| `RATE_LIMIT_ENABLED`, `RATE_LIMIT_AUTH_PER_MINUTE`, `RATE_LIMIT_API_PER_MINUTE` | rate limiting | true, 10, 120 |

## Try the API

```bash
curl -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"jay@example.com","password":"a-long-password-123","firstName":"Jay","lastName":"Doe"}'

curl localhost:8080/api/auth/me -H "Authorization: Bearer <token>"

# create a portfolio, then record a deposit
curl -X POST localhost:8080/api/portfolios -H "Authorization: Bearer <token>" -H 'Content-Type: application/json' \
  -d '{"name":"Long term","baseCurrency":"USD"}'
curl -X POST localhost:8080/api/portfolios/1/transactions -H "Authorization: Bearer <token>" -H 'Content-Type: application/json' \
  -d '{"type":"DEPOSIT","amount":5000,"executedAt":"2026-01-02T10:00:00Z"}'
```

With `APP_MAIL_LOG_ONLY=true` the verification link is written to the backend log, so you can copy the token into `POST /api/auth/verify-email`. Never enable this in production.


## Financial calculation rules

- Money and quantities are `BigDecimal` / `NUMERIC`, never floating point.
- Average cost method. BUY adds `qty x price + fee` to cost. SELL removes cost at the current average; realized P&L = `qty x price - fee - cost removed`.
- Dividends = `shares x per-share amount - fee`, and do not change cost basis.
- Internal scale 8, reported scale 4, `HALF_UP`.
- Overselling raises `InsufficientHoldingsException` (HTTP 422).

## Market data policy

Real prices are used only when `MARKET_DATA_API_KEY` is configured (Finnhub). Otherwise the app serves deterministic development data that is flagged `simulated = true` in the API and stored with `source = SIMULATED`, and alert notifications on it say "(simulated price)". Simulated and real prices are never mixed silently. If the live source fails, the last stored price is returned with `stale = true`.

## Security notes

- Secrets come from environment variables only; `.env` is git-ignored.
- `JWT_SECRET` shorter than 32 characters stops the app at startup.
- Login failures return one generic message to prevent account enumeration.
- Stateless API, so CSRF protection is disabled by design (no cookies are used).
- Hibernate `ddl-auto=validate`: schema changes go through Flyway migrations only.
