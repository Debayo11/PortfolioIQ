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
