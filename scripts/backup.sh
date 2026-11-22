#!/usr/bin/env bash
# Writes a compressed, consistent PostgreSQL backup.
#   ./scripts/backup.sh                       -> backups/portfolioiq-<UTC timestamp>.sql.gz
#   ./scripts/backup.sh /path/to/file.sql.gz
# For production use the same compose files as the running stack:
#   COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml" ./scripts/backup.sh
set -euo pipefail
cd "$(dirname "$0")/.."
COMPOSE="${COMPOSE:-docker compose}"
OUT="${1:-backups/portfolioiq-$(date -u +%Y%m%dT%H%M%SZ).sql.gz}"
mkdir -p "$(dirname "$OUT")"

# pg_dump takes a consistent snapshot without stopping the app. Credentials come from the container's own environment.
$COMPOSE exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-privileges' | gzip > "$OUT"

# Refuse to report success on an empty or truncated file.
if ! gzip -t "$OUT" || [ "$(gzip -dc "$OUT" | wc -c)" -lt 1000 ]; then
  echo "Backup looks incomplete: $OUT" >&2
  exit 1
fi
echo "Backup written: $OUT ($(du -h "$OUT" | cut -f1))"
echo "Copy it somewhere other than this server, and test a restore before you need one."
