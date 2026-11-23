PortfolioIQ

PortfolioIQ is an investment and portfolio management application for keeping track of investments, transactions and portfolio performance. It has a Spring Boot API backed by PostgreSQL and a React frontend that works on phones, tablets and desktop browsers.

The application supports portfolio and transaction management, market quotes, watchlists, price alerts, notifications and performance analytics. It also includes role-based administration tools.

Features

Portfolios and transactions

- Create and manage multiple portfolios.
- Add assets from a shared asset catalogue.
- Record buys, sells, dividends, deposits, withdrawals and fees.
- View holdings, invested cost, market value, realized and unrealized profit or loss.
- Transfer holdings between portfolios while carrying their cost basis.
- Filter and sort transaction history.

Market information

- Retrieve market quotes from Finnhub when an API key is configured.
- Use clearly labelled simulated market data when no key is configured.
- View quote history, daily price changes and available market statistics.
- Create watchlists and price alerts.
- Receive in-app notifications for alerts and account activity.

Analytics

- View portfolio value and performance history.
- Review returns, volatility and maximum drawdown.
- See allocation by asset and asset type.
- Compare portfolios and use an asset as a benchmark.
- Review individual asset performance, including realized gains, unrealized gains and dividends.

Accounts and administration

- Register, sign in and verify an email address.
- Recover an account using a password reset link.
- Use Investor, Analyst and Administrator roles.
- Manage users and roles from the administrator area.
- Review audit activity, system information, API usage and market-data settings.

Installable frontend

The frontend is a Progressive Web App (PWA). It can be installed from supported browsers and adapts its navigation and layout for smaller and larger screens. The app shell can load offline, but financial data is not served from an offline cache.

Technology

Area| Technologies
Backend| Java 21, Spring Boot, Spring Security, JWT
Database| PostgreSQL, Flyway
Caching and rate limiting| Redis
Frontend| React, TypeScript, Vite, Tailwind CSS
Charts| Recharts
Testing| JUnit, Testcontainers, Playwright
Build and deployment| Maven, Docker Compose, Caddy, GitHub Actions

How it is structured

The frontend communicates with the backend through a REST API. The backend handles authentication, business rules and financial calculations, while PostgreSQL stores application data. Redis is used for quote caching and rate limiting.

React frontend
      |
      | REST API / JSON + JWT
      v
Spring Boot backend
      |
      +------ PostgreSQL (Flyway migrations)
      |
      +------ Redis (cache and rate limiting)
      |
      +------ Finnhub (when configured)

The backend is organised into controller, service and repository layers. Financial calculation logic is kept separate from the web framework so it can be tested independently.

Project structure

portfolioiq/
├── backend/       Spring Boot application and tests
├── frontend/      React and TypeScript PWA
├── e2e/           Playwright end-to-end tests
├── scripts/       Schema, backup, restore and environment scripts
├── docs/          Architecture, testing and deployment guides
├── .github/       CI workflow and Dependabot configuration
├── docker-compose.yml
├── .env.example
├── Caddyfile
└── SECURITY.md

Getting started

Requirements

To run the full application with Docker, you need:

- Docker
- Docker Compose

For a local setup without Docker, install:

- Java 21
- Maven 3.9 or later
- PostgreSQL 16
- Redis 7
- Node.js and npm (for the frontend)

Run with Docker Compose

1. Clone the repository and move into the project directory.

2. Copy the example environment file:
   
   cp .env.example .env

3. Edit ".env". At minimum, set a strong "POSTGRES_PASSWORD" and a secure "JWT_SECRET".

4. Build and start the services:
   
   docker compose up --build

When the services are running, the default local addresses are:

Service| URL
Frontend| http://localhost:3000
Backend API| http://localhost:8080
Swagger UI| http://localhost:8080/swagger-ui.html
Health check| http://localhost:8080/actuator/health

Run the backend locally

Start PostgreSQL and Redis, create the "portfolioiq" database and configure the connection details. From the project root:

cd backend

export DB_URL=jdbc:postgresql://localhost:5432/portfolioiq
export DB_USER=portfolioiq
export DB_PASSWORD=your-database-password
export JWT_SECRET=your-long-secret-value

mvn spring-boot:run

Flyway applies the database migrations when the application starts.

Run the frontend locally

In another terminal:

cd frontend
npm install
npm run dev

The Vite development server runs at http://localhost:5173 and proxies "/api" requests to the backend on port 8080 by default.

More frontend-specific information is available in "frontend/README.md" (frontend/README.md).

Creating the first administrator

Public registration creates an account with the "INVESTOR" role. To bootstrap the first administrator:

1. Set "BOOTSTRAP_ADMIN_EMAIL" in ".env" to the email address you intend to use.
2. Register with that email address.
3. Verify the email address.
4. The account is promoted to Administrator if there is no other enabled administrator.

The bootstrap setting only works while the system has no enabled administrator. Remove the setting after the account has been created.

For local development, "APP_MAIL_LOG_ONLY=true" writes verification links to the backend logs instead of sending email. Do not use this setting in production.

Market data

PortfolioIQ uses Finnhub for live quotes when "MARKET_DATA_API_KEY" is configured. If the key is not set, the application uses deterministic simulated data for development.

The API identifies simulated data with a "simulated" flag, and the interface labels it accordingly. Live and simulated prices are not silently mixed. If the live provider is unavailable, the application can return the last stored quote with a stale-data flag.

The provider has request limits, so the refresh process is capped. Some market fields may not be available from the provider and are left empty rather than filled with made-up values.

Financial calculations

Financial amounts and quantities use Java "BigDecimal" and PostgreSQL "NUMERIC", rather than floating-point values.

The application uses the average-cost method:

- A buy adds the purchase value and fee to the holding's cost.
- A sale removes cost at the current average cost and calculates realized profit or loss.
- Dividends are recorded separately and do not change the holding's cost basis.
- Selling more shares than are held is rejected.

The calculation rules and precision are documented in the backend code and covered by tests.

API

The backend exposes a REST API. Swagger UI is available at:

http://localhost:8080/swagger-ui.html

Some of the main endpoint groups are:

Area| Example endpoints| Purpose
Authentication| "/api/auth/**"| Registration, login, verification and password recovery
Portfolios| "/api/portfolios/**"| Portfolio details, holdings and transactions
Assets| "/api/assets"| Search the asset catalogue
Market| "/api/market/**"| Market status, quotes and price history
Watchlists| "/api/watchlists/**"| Create and manage watchlists
Alerts| "/api/alerts/**"| Create and manage price alerts
Notifications| "/api/notifications/**"| View and mark notifications as read
Transfers| "/api/transfers/**"| Move holdings between portfolios
Analytics| "/api/analytics/**"| Performance, allocation and portfolio comparisons
Administration| "/api/admin/**"| User management, audit logs and system information

Protected endpoints require a valid bearer token. Administrator endpoints require the Administrator role. Full request and response schemas are available in Swagger UI.

Configuration

Application settings are supplied through environment variables. The full list and example values are in ".env.example".

A few settings you may want to configure:

Variable| Purpose
"POSTGRES_PASSWORD"| PostgreSQL password
"JWT_SECRET"| Secret used to sign JWTs; use a long, random value
"MARKET_DATA_API_KEY"| Finnhub API key; leave empty to use simulated data
"MARKET_REFRESH_INTERVAL_MS"| Interval between market-data refreshes
"MARKET_MAX_REQUESTS_PER_REFRESH"| Maximum assets requested per refresh
"MARKET_CACHE_TTL_SECONDS"| Lifetime of cached market quotes
"RATE_LIMIT_ENABLED"| Enables or disables API rate limiting

Do not commit ".env" or put production secrets in source control.

Tests

The repository includes backend unit and integration tests, frontend checks and browser-based end-to-end tests.

# Check entity definitions against database migrations
python3 scripts/check_schema.py

# Backend unit tests
cd backend && mvn test

# Backend integration tests (requires Docker)
cd backend && mvn verify

# Frontend checks
cd frontend
npm run typecheck
npm test
npm run build

The integration tests use Testcontainers to run PostgreSQL and Redis. Playwright tests cover browser workflows on desktop and phone-sized profiles.

See "docs/TESTING.md" (docs/TESTING.md) for test instructions and the current testing status.

Known limitations

A few current limitations are worth knowing about:

- Finnhub is the only live market-data provider currently configured.
- Live historical price data builds from prices stored by the application after it starts; provider candle history is not used.
- Price alerts trigger once and then deactivate.
- Analytics focus on investment holdings. Cash balances are not tracked as holdings.
- There is no currency conversion. Portfolios can only be combined when they use the same base currency.
- The application does not enforce a cash balance when recording buys or withdrawals.
- Transactions cannot be edited. A transaction can be deleted and entered again, subject to dependency checks.
- Analytics use daily data points and do not provide forecasts or projections.
- There are no refresh tokens yet. Users sign in again when their JWT expires.
- Some administrator system and API usage figures are held in memory and reset when the server restarts.

Deployment

Production configuration includes HTTPS through Caddy, hardened Docker services, backup and restore scripts, and an environment checker.

Before deploying, review:

- "Deployment guide" (docs/DEPLOYMENT.md)
- "Architecture notes" (docs/ARCHITECTURE.md)
- "Testing notes" (docs/TESTING.md)
- "Security policy" (SECURITY.md)

Use production secrets and configure email delivery and HTTPS before making the application publicly available.