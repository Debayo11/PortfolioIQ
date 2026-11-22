# Security

## Reporting a vulnerability

Please report security problems privately (for example through GitHub's "Report a vulnerability" feature on this
repository) rather than in a public issue. Include steps to reproduce and the version or commit.

## How the application protects data

- Passwords are hashed with BCrypt (cost 12). Tokens for email verification and password reset are random, single
  use, expire quickly, and are stored only as SHA-256 hashes
- Sign-in uses short-lived signed JWTs. Roles and the enabled flag are read from the database on every request, so
  disabling an account or changing a role takes effect immediately
- Every portfolio, watchlist, alert and notification query is scoped to the signed-in user; someone else's data looks
  like it does not exist (404), so identifiers cannot be probed
- Sign-in, registration and password endpoints are rate limited in Redis; failures return one generic message
- The administrator area is protected in three layers (URL rule, method security, hidden in the UI), cannot lock out
  the last administrator, and every action is written to the audit log
- Money is stored and calculated as exact decimals; the database enforces non-negative amounts and valid transaction shapes
- Secrets come from environment variables only. The market data API key is never returned by any endpoint, and logs
  shown to administrators have tokens, passwords and keys masked
- The web server sends a strict Content-Security-Policy; production traffic is HTTPS with HSTS; containers run as a
  non-root user with a read-only filesystem and dropped capabilities
- Dependencies are watched by Dependabot, and CI runs on every change

See `docs/DEPLOYMENT.md` for the production checklist.
