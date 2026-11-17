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
