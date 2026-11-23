#!/usr/bin/env bash
# Checks an env file before deployment. Exit code 1 means something must be fixed.
#   ./scripts/check-env.sh .env prod      (default mode is prod)
#   ./scripts/check-env.sh .env dev
set -u
FILE="${1:-.env}"
MODE="${2:-prod}"
[ -f "$FILE" ] || { echo "No such file: $FILE" >&2; exit 2; }

get() { grep -E "^$1=" "$FILE" | tail -n1 | cut -d= -f2- | tr -d '\r' | sed -e 's/^"//' -e 's/"$//'; }
fail=0
bad()  { echo "FAIL  $1"; fail=1; }
good() { echo "ok    $1"; }
note() { echo "note  $1"; }

pw="$(get POSTGRES_PASSWORD)"
if [ "${#pw}" -ge 16 ] && [ "$pw" != "change-me" ]; then good "POSTGRES_PASSWORD is set and long enough"; else bad "POSTGRES_PASSWORD must be at least 16 characters and not the example value"; fi

jwt="$(get JWT_SECRET)"
if [ "${#jwt}" -ge 48 ] && [ "$jwt" != "change-me-to-a-long-random-string-of-48-chars-or-more" ]; then good "JWT_SECRET is set and long enough"; else bad "JWT_SECRET must be at least 48 random characters (openssl rand -base64 48)"; fi

if [ "$MODE" = "prod" ]; then
  [ -n "$(get DOMAIN)" ] && good "DOMAIN is set" || bad "DOMAIN must be set (for example app.example.com)"
  case "$(get FRONTEND_URL)" in https://*) good "FRONTEND_URL uses https" ;; *) bad "FRONTEND_URL must start with https:// in production (the prod compose file also sets it from DOMAIN)" ;; esac
  [ -n "$(get SPRING_MAIL_HOST)" ] && good "SPRING_MAIL_HOST is set" || bad "SPRING_MAIL_HOST must be set so verification and reset emails are delivered"
  [ "$(get APP_MAIL_LOG_ONLY)" = "true" ] && bad "APP_MAIL_LOG_ONLY=true prints one-time links to the log; set it to false" || good "APP_MAIL_LOG_ONLY is off"
  [ "$(get REQUIRE_VERIFIED_EMAIL)" = "true" ] && good "REQUIRE_VERIFIED_EMAIL is on" || note "REQUIRE_VERIFIED_EMAIL is not true in this file (docker-compose.prod.yml forces it on)"
  [ -n "$(get MARKET_DATA_API_KEY)" ] && good "MARKET_DATA_API_KEY is set (real prices)" || note "MARKET_DATA_API_KEY is empty: the app will show clearly labelled SIMULATED prices"
  [ -n "$(get BOOTSTRAP_ADMIN_EMAIL)" ] && note "BOOTSTRAP_ADMIN_EMAIL is set: once the first administrator exists you can clear it"
fi

[ "$fail" -eq 0 ] && echo "Environment looks ready." || echo "Fix the items marked FAIL."
exit "$fail"
