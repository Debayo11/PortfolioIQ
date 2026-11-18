# Architecture notes

## Key decisions

- **Flyway owns the schema.** Hibernate validates only. Constraints (CHECKs, FKs, unique indexes) live in SQL so integrity does not depend on application code.
- **Average-cost accounting in a pure function.** `PositionCalculator` replays events deterministically, so valuation can always be recomputed from the transaction ledger rather than stored and risking drift.
- **Totals are derived, not duplicated.** Holdings and P&L are computed from `transactions`; `dividends` links 1:1 to a DIVIDEND transaction.
- **Simulated data is flagged in the data model** (`market_prices.simulated`), not just in the UI.
- **Roles via join table** so a user can hold several roles; admin assignment is an admin-only operation.

## Entity relationships

```
users 1--* portfolios 1--* transactions *--1 assets
users *--* roles (user_roles)
users 1--* watchlists 1--* watchlist_items *--1 assets
users 1--* price_alerts *--1 assets
users 1--* notifications
assets 1--* market_prices
transactions 1--1 dividends
users 1--* user_tokens, audit_logs
```

## Authorization model

Every portfolio-scoped query will filter by the authenticated user's id in the repository layer (Stage 2), so ownership is enforced in the query rather than checked after loading data.

## Stage 2 decisions

- **Ownership is enforced in the query.** `PortfolioRepository` only exposes lookups that include the user id, so a portfolio you do not own is indistinguishable from one that does not exist (404, no enumeration).
- **Write path locks the portfolio row** (`PESSIMISTIC_WRITE`) before validating holdings. Two concurrent sells of the same shares serialise instead of both succeeding.
- **Validation replays history.** A new asset transaction is validated by running the full ledger plus the new event through `PositionCalculator`, so back-dated sales and deletions are checked the same way as live ones.
- **`TransactionRules` is pure Java.** Total, precision and sign rules live outside Spring and are tested without a container.
- **Audit entries share the caller's transaction.** If an operation rolls back, so does its audit row. Failed logins are written outside a transaction so they persist.
- **One-time tokens are stored hashed.** A leaked database cannot be used to reset passwords or verify emails.
- **Errors are uniform.** `GlobalExceptionHandler` extends Spring's `ResponseEntityExceptionHandler`, so framework errors (404, 405, malformed JSON) use the same `ApiError` shape instead of surfacing as 500s.

## Stage 3 decisions

- **Provider abstraction.** `MarketDataProvider` has a simulated and a Finnhub implementation, chosen at startup by whether an API key exists. Everything downstream (cache, valuation, alerts) is provider-agnostic.
- **Simulated data is a first-class, labelled source.** It is flagged in the data model (`market_prices.simulated`), in every quote, and in alert text, and is deterministic so demos and tests are repeatable. The generator is pure Java with no framework dependency.
- **Read path: cache, then provider, then last stored price.** A cache or provider failure degrades to a stale-flagged price instead of an error. Redis failures never break a request (fail open), including for rate limiting.
- **Refresh job is capped and locked.** A Redis lock prevents several instances refreshing at once, and a per-cycle cap respects free-tier API limits. Alert assets are refreshed first, then watchlists, then held assets.
- **Transfers preserve cost basis.** `TRANSFER_OUT` removes quantity and cost at the average cost; `TRANSFER_IN` adds quantity at that carried unit cost. Neither realizes profit or loss. Both legs share a `transfer_group` id, enforced by a CHECK constraint, so they are created and undone as a pair. Portfolios are locked in ascending id order to avoid deadlocks.
- **Alert rules are pure** (`AlertRules`) and one-shot: a triggered alert deactivates and notifies in the same transaction.
- **Valuation is derived, never stored.** Market value and unrealized P&L are computed from the ledger and the latest price on each request.

## Stage 4 decisions

- **A framework-free engine.** `SeriesBuilder`, `Returns`, `RiskMetrics`, `Diversification` and `Breakdown` have no Spring or database dependencies, so every formula is unit tested against hand-calculated values. `AnalyticsService` only loads data, calls the engine and shapes responses.
- **One source of truth for cost basis.** The daily series replays each asset through `PositionCalculator`, so analytics always agree with holdings and the portfolio summary.
- **Time-weighted, not money-weighted, for performance.** Each day's return is `(end value - net flow + dividends) / previous value - 1`, chained into an index starting at 100. Drawdown and volatility are computed on that index, so contributions and withdrawals cannot masquerade as gains or losses.
- **Transfers between your own portfolios net to zero** when portfolios are combined, because both legs are valued at the same market price on the same day.
- **Honest about missing data.** When no market price exists for a day, the last trade price is used and the day is flagged `approximated`. The report lists warnings (simulated prices, estimated days, unpriced positions, a benchmark with no history) instead of presenting a clean-looking number.
- **Today's point equals the live valuation.** The latest quote overrides today's historical close so the final chart point matches the holdings and summary endpoints.
- **Currencies are never mixed.** Aggregation happens within one base currency; other portfolios are returned in `excludedPortfolios`. Comparison reports percentages only and warns when currencies differ.
- **History is separate from projection.** Every report carries a disclaimer, and no endpoint forecasts anything.

## Stage 5A decisions

- **One codebase for phones and computers: a Progressive Web App.** It installs from the browser on iOS, Android, Windows, macOS and Linux, updates itself, and avoids store review. Native wrappers (Capacitor, Tauri) can be added later around the same build.
- **Same-origin API access.** The Vite dev server and nginx both proxy `/api` to the backend, so the browser needs no CORS and tokens never cross origins. CORS is available but off by default for native shells.
- **The service worker caches the app shell only.** Financial data always comes from the network; an offline banner tells people why figures may be out of date.
- **Responsive by structure, not by shrinking.** A bottom tab bar and card lists on phones, a sidebar and tables on computers, safe-area padding for notches, and 16px inputs so iOS does not zoom.
- **The server is the authority.** Client validation mirrors server rules for instant feedback; money maths is never done in the browser, which only formats numbers.
- **Server state through React Query.** Caching, background refresh and retry live in one place. Client errors (4xx) are not retried.
- **Small, dependency-light UI.** Tailwind design tokens (navy and charcoal surfaces, emerald gains, red losses), a handful of primitives, Recharts for charts, no component library.
