# Deploying PortfolioIQ

This guide takes a fresh Linux server to a running, HTTPS-secured installation that phones can install as an app.

## What you need

- A server with Docker and Docker Compose v2 (2 GB of memory is comfortable)
- A domain name whose DNS `A` (and `AAAA`) record points at the server
- Ports 80 and 443 reachable from the internet (Caddy uses 80 to obtain the certificate)
- An SMTP account for verification and password-reset emails (any provider)
- Optional: a [Finnhub](https://finnhub.io) API key for real market prices. Without one the app serves clearly labelled **simulated** data

## 1. Configure

```bash
git clone <your-repo-url> portfolioiq && cd portfolioiq
cp .env.example .env
```

Edit `.env`:

| Setting | Value |
|---|---|
| `POSTGRES_PASSWORD` | 16+ random characters (`openssl rand -hex 16`) |
| `JWT_SECRET` | 48+ random characters (`openssl rand -base64 48`) |
| `DOMAIN` | for example `app.example.com` |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | your SMTP account |
| `MAIL_FROM` | a sender address your SMTP provider allows |
| `BOOTSTRAP_ADMIN_EMAIL` | the address that becomes the first administrator |
| `MARKET_DATA_API_KEY` | optional |

Then check it. The script fails on placeholder secrets, missing SMTP, non-HTTPS URLs and other mistakes:

```bash
./scripts/check-env.sh .env prod
```

## 2. Start

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps     # everything should become "healthy"
```

Caddy obtains a certificate on first request, so the first visit to `https://<DOMAIN>` may take a few seconds.
Only ports 80 and 443 are published. The database, cache, backend and web server stay on the private Docker network.
The first start applies the database migrations automatically.

## 3. Create the first administrator

Open the site, register with the address you put in `BOOTSTRAP_ADMIN_EMAIL`, and click the link in the verification
email. The account becomes an administrator when its email is verified, and only while no administrator exists. After
that you can remove the setting. Further administrators are created from **Administration, Users**.

## 4. Install on a phone or computer

Visit `https://<DOMAIN>`. Chrome and Edge show an Install button. On iPhone and iPad choose Share, then Add to Home
Screen. This needs HTTPS, which the production stack provides.

## Operating it

**Updates.** Take a backup, then:

```bash
git pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Database migrations run automatically. The installed app updates itself in the background and uses the new version on
its next launch.

**Backups.** Run on a schedule, and copy the files off the server:

```bash
COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml" ./scripts/backup.sh
# cron example, every night at 02:30:
# 30 2 * * * cd /srv/portfolioiq && COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml" ./scripts/backup.sh
```

**Restore** (replaces all data, asks for confirmation; try it on a spare machine before you need it):

```bash
COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml" ./scripts/restore.sh backups/portfolioiq-<timestamp>.sql.gz
```

**Logs.** `docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f backend`. Every line carries a request
id, and every response has an `X-Request-Id` header, so a problem reported by a user can be traced to its log lines.
Administrators can also see recent warnings and errors, API usage and the audit log in the app.

**Monitoring.** Point an uptime monitor at `https://<DOMAIN>/` (the actuator endpoints are not exposed to the internet).
Containers have health checks, so `docker compose ps` shows their state.

## Things to know

- **Several backend instances.** Rate limiting and the price-refresh lock use Redis and work across instances. The
  system log, API usage figures and last-refresh result in the admin panel are per instance and reset on restart.
- **Client addresses.** Caddy passes the real address to nginx, which passes it to the backend, which trusts it only
  from private-network proxies. Rate limiting and the audit log therefore see the real client.
- **Sessions** last 60 minutes (`JWT_EXPIRATION_MINUTES`). There are no refresh tokens yet.
- **Redis** holds only a cache and rate-limit counters; losing it loses nothing important.
- **Swagger UI** is switched off in production. Set `SWAGGER_ENABLED=true` temporarily if you need it.
- **Market data.** Without an API key all prices are simulated and labelled as such everywhere.

## Security checklist

- [ ] `./scripts/check-env.sh .env prod` passes
- [ ] `.env` is not committed (it is git-ignored) and is readable only by the deploying user (`chmod 600 .env`)
- [ ] DNS is correct and `https://<DOMAIN>` shows a valid certificate
- [ ] Only ports 80 and 443 are open in the firewall
- [ ] A backup has been taken and a restore has been tested
- [ ] The first administrator exists and `BOOTSTRAP_ADMIN_EMAIL` has been cleared
- [ ] Dependabot pull requests are reviewed regularly

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `docker compose` complains that a variable is required | `DOMAIN` or `SPRING_MAIL_HOST` is missing from `.env` |
| Backend stays unhealthy | `docker compose ... logs backend`: usually a wrong `POSTGRES_PASSWORD` or a short `JWT_SECRET` (the app refuses to start under 32 characters) |
| No verification email | Check the SMTP settings; failures are logged as warnings and shown under Administration, System |
| Browser says the certificate is invalid | DNS does not point at this server yet, or port 80 is blocked |
| Everyone gets "Too many requests" | Client addresses are not being forwarded; make sure traffic goes through Caddy |
