#!/usr/bin/env bash
# Provisions local secrets into .env: a Grafana admin password and the PostgreSQL
# passwords (the instance's superuser, the application's own role, and Grafana's
# read-only role). It also points Docker Compose at the development compose file, so
# plain `docker compose` commands work from the repository root. Safe to re-run: each
# secret is generated only if it is missing, so running this after adding a new service
# fills in just the new keys.
set -euo pipefail

cd "$(dirname "$0")/.."

touch .env
chmod 600 .env

# Appends KEY=value to .env unless KEY is already set there.
ensure_env() {
  local key="$1" value="$2"
  if grep -q "^${key}=" .env; then
    echo "  $key already set, left alone"
  else
    echo "${key}=${value}" >> .env
    echo "  $key written"
  fi
}

# Letters and digits only, so a password needs no quoting in a URL, in .env or in SQL.
password() {
  openssl rand -base64 24 | tr -d '/+=' | head -c 24
}

echo "Docker Compose:"
# Compose reads COMPOSE_FILE from the .env in the directory it runs in, and then takes
# the other variables from that same file, so `docker compose up -d` from the
# repository root starts the development services. An explicit -f overrides it.
ensure_env COMPOSE_FILE docker/dev/compose.dev.yaml

echo "Grafana:"
ensure_env GRAFANA_ADMIN_PASSWORD "$(openssl rand -base64 18)"

echo "PostgreSQL:"
# The roles are created from these on the container's first start with an empty
# volume; see docker/postgres/initdb. Changing them later needs ALTER ROLE too.
ensure_env POSTGRES_DB iot_pipeline
ensure_env POSTGRES_ADMIN_PASSWORD "$(password)"
ensure_env POSTGRES_APP_USERNAME iot_pipeline
ensure_env POSTGRES_APP_PASSWORD "$(password)"
ensure_env POSTGRES_GRAFANA_PASSWORD "$(password)"

echo
echo "Secrets are in .env, which is gitignored. Keep it out of version control."
