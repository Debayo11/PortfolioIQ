#!/usr/bin/env bash
# Restores a backup made by backup.sh. THIS REPLACES ALL CURRENT DATA.
#   ./scripts/restore.sh backups/portfolioiq-20260101T000000Z.sql.gz
#   ./scripts/restore.sh <file> --yes        (skip the confirmation prompt)
set -euo pipefail
cd "$(dirname "$0")/.."
COMPOSE="${COMPOSE:-docker compose}"
FILE="${1:?usage: restore.sh <backup.sql.gz> [--yes]}"
[ -f "$FILE" ] || { echo "No such file: $FILE" >&2; exit 2; }
gzip -t "$FILE" || { echo "Not a valid gzip file: $FILE" >&2; exit 2; }

if [ "${2:-}" != "--yes" ]; then
  read -r -p "This deletes ALL current data and loads $FILE. Type 'restore' to continue: " answer
  [ "$answer" = "restore" ] || { echo "Cancelled."; exit 1; }
fi

echo "Stopping the backend so nothing writes during the restore..."
$COMPOSE stop backend

echo "Replacing the database contents..."
$COMPOSE exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"'
gzip -dc "$FILE" | $COMPOSE exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1' > /dev/null

echo "Starting the backend..."
$COMPOSE start backend
echo "Restore complete."
