# Testing

| Layer | What it checks | Command | Needs |
|---|---|---|---|
| Schema check | every JPA entity matches the Flyway migrations (tables, columns, types, join tables, foreign keys) | `python3 scripts/check_schema.py` | Python 3 |
| Backend unit tests | money maths, validation, analytics, security rules, services with mocked repositories | `cd backend && mvn test` | Java 21, Maven |
| Backend integration tests | the real application on real PostgreSQL 16 and Redis 7: migrations, SQL, security, concurrency, rate limiting | `cd backend && mvn verify` | Docker |
| Frontend | strict type-check, unit tests (formatting, sessions, validation), production build | `cd frontend && npm run typecheck && npm test && npm run build` | Node 22 |
| End to end | real browsers against the whole stack: sign-up with a real email, password reset, an investing journey, the phone and desktop layouts, the installable-app manifest | see below | Docker, Node 22 |

Integration tests are skipped automatically when Docker is not running, so `mvn verify` still works on a machine
without it. In CI they always run.

## End-to-end tests

```bash
docker compose -f docker-compose.yml -f docker-compose.override.yml -f docker-compose.e2e.yml up -d --build --wait
cd e2e && npm install && npx playwright install --with-deps chromium && npx playwright test
docker compose -f docker-compose.yml -f docker-compose.override.yml -f docker-compose.e2e.yml down -v
```

The e2e stack adds Mailpit, a mail catcher. Tests read the real verification and reset emails from it, so the whole
email path is exercised. Two projects run: desktop Chrome and a Pixel 7 phone profile.

## What the integration tests prove

- Flyway applies all four migrations and Hibernate's schema validation passes
- The database itself rejects bad data (negative quantities, trades without an asset, malformed transfer legs)
- Registration, sign-in, email verification and password reset work, tokens are single use, errors never reveal whether an account exists
- Buy, sell, dividend and transfer maths match hand calculations, including fees and cost basis
- **Eight simultaneous sales of the same shares: exactly one succeeds** (row locking works)
- Users cannot see or change each other's portfolios, watchlists, alerts or notifications
- Administrators can manage users; every guard rail holds (no self lock-out); disabling an account blocks its existing token immediately; roles change takes effect without a new login
- The first administrator can only be created through a verified email
- Quotes, history and alerts work with simulated data and are labelled as simulated; an alert fires once and notifies its owner
- Analytics reports are internally consistent and match the live valuation
- Rate limiting returns 429 with `Retry-After` against a real Redis

## Honest status

All of this was written without the ability to run Maven, Docker or a browser in the authoring environment. What *was*
run there, and passed:

- the framework-free core (money maths, analytics, risk, alert rules, simulation, log redaction, usage aggregation) executed directly on the JDK
- the schema check, including deliberate-mistake tests showing it catches real problems
- strict TypeScript type-checking of the frontend and e2e code against stand-in library typings, a production-style bundle, and the frontend unit tests
- syntax checks of every Java file, YAML validation of every compose and workflow file, and the deployment scripts

The first full `mvn verify`, `npm run build` and Playwright run happen on your machine or in CI. Expect to fix a small
number of issues at that point; every failure message will point at the cause.

## Not covered

- Load and soak testing; the rate limiter and refresh job are tested for behaviour, not capacity
- Visual regression of the interface
- Real market data (tests use the simulated provider; the Finnhub adapter was written against its documentation)
- Browsers other than Chromium, and real iOS or Android devices
