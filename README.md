# PortfolioIQ

Investment and portfolio management platform: Spring Boot + PostgreSQL backend, React/TypeScript frontend (in progress).

> **Status: Stage 2 of 6.** Built incrementally, with each stage verified before the next. Nothing listed as "done" below is a stub.

## Roadmap

| Stage | Scope | Status |
|------:|-------|--------|
| 1 | Architecture, PostgreSQL schema (Flyway), Docker/Compose, JWT auth + roles, financial calculation core + tests | **Done** |
| 2 | Portfolios, assets, transactions API, ownership checks, audit logging, email verification and password recovery | **Done** |
| 3 | Market data (real API if key set, otherwise labelled simulated data), watchlists, price alerts, notifications, Redis caching, rate limiting, asset transfers between portfolios | Next |
| 4 | Analytics engine: ROI, volatility, max drawdown, diversification, benchmark comparison | Planned |
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

## Known limitations (deliberate, to be addressed in later stages)

- No market prices yet, so no market value or unrealized P&L in the portfolio summary (Stage 3)
- No transfer of assets between portfolios yet (needs a cost-basis-preserving transfer type, Stage 3)
- No currency conversion: record prices in the portfolio's base currency
- Cash balance is not enforced: withdrawals and buys are not checked against deposits
- Transactions cannot be edited, only deleted and re-entered
- Existing JWTs stay valid until they expire after a password reset (default 60 minutes)
- No rate limiting yet (Stage 3). The frontend routes the emailed links point to (`/verify-email`, `/reset-password`) arrive in Stage 5; until then POST the token to the API
- Backend automated tests are unit tests. Integration tests against a real PostgreSQL arrive in Stage 6

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
│       │   ├── transaction/ transactions, dividends, validation service
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

Full request/response schemas are in Swagger UI.

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

Real prices are used only when `MARKET_DATA_API_KEY` is configured. Otherwise the app serves data flagged `simulated = true` and the UI labels it clearly. Market data is never invented silently.

## Security notes

- Secrets come from environment variables only; `.env` is git-ignored.
- `JWT_SECRET` shorter than 32 characters stops the app at startup.
- Login failures return one generic message to prevent account enumeration.
- Stateless API, so CSRF protection is disabled by design (no cookies are used).
- Hibernate `ddl-auto=validate`: schema changes go through Flyway migrations only.
