# PortfolioIQ

Investment and portfolio management platform: Spring Boot + PostgreSQL backend, React/TypeScript frontend (in progress).

> **Status: Stage 1 of 6.** Built incrementally, with each stage verified before the next. Nothing listed as "done" below is a stub.

## Roadmap

| Stage | Scope | Status |
|------:|-------|--------|
| 1 | Architecture, PostgreSQL schema (Flyway), Docker/Compose, JWT auth + roles, financial calculation core + tests | **Done** |
| 2 | Portfolios, assets, transactions API (buy/sell/dividend/deposit/withdrawal/fee), ownership checks, audit logging, email verification and password recovery | Next |
| 3 | Market data (real API if key set, otherwise labelled simulated data), watchlists, price alerts, notifications, Redis caching, rate limiting | Planned |
| 4 | Analytics engine: ROI, volatility, max drawdown, diversification, benchmark comparison | Planned |
| 5 | React + TypeScript + Tailwind + Recharts frontend, admin panel | Planned |
| 6 | Integration tests (Testcontainers), production config, CI | Planned |

## What works in Stage 1

- `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me`
- BCrypt (cost 12) password hashing, stateless JWT (HS256), role-based access (`ADMINISTRATOR`, `INVESTOR`, `ANALYST`)
- Self-registration always yields `INVESTOR`; `/api/admin/**` requires `ADMINISTRATOR`
- Consistent JSON error responses; internals are never leaked
- Full PostgreSQL schema (13 tables) managed by Flyway; Hibernate only validates
- `PositionCalculator`: average cost, realized/unrealized P&L, dividend income, fees. `BigDecimal` only, with insufficient-holdings protection
- Swagger UI at `/swagger-ui.html`, health at `/actuator/health`

**Not yet implemented:** email verification and password recovery (the `user_tokens` table exists, the flow arrives in Stage 2), rate limiting (Stage 3), and the entire frontend.

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
│       │   ├── auth/        controller, service, DTOs
│       │   ├── config/      SecurityConfig, OpenApiConfig
│       │   ├── security/    JwtService, JwtAuthenticationFilter, UserDetailsService
│       │   ├── user/        User, Role, repositories
│       │   ├── common/      ApiError, ApiException, GlobalExceptionHandler
│       │   └── finance/     PositionCalculator, Position, TradeEvent
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/migration/V1__init_schema.sql
│       └── test/java/com/portfolioiq/finance/PositionCalculatorTest.java
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

## Try the API

```bash
curl -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"jay@example.com","password":"a-long-password-123","firstName":"Jay","lastName":"Doe"}'

curl localhost:8080/api/auth/me -H "Authorization: Bearer <token>"
```

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
