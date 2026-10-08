#!/usr/bin/env bash
# Generates an offline InfluxDB 3 admin token (loaded by the server at startup via
# --admin-token-file) and writes it, with a Grafana admin password, to .env.
# Safe to re-run: does nothing if the token file already exists.
set -euo pipefail

cd "$(dirname "$0")/.."

SECRETS_DIR=docker/influxdb3/secrets
TOKEN_FILE="$SECRETS_DIR/admin-token.json"
IMAGE=influxdb:3.12-core

if [[ -e "$TOKEN_FILE" ]]; then
  echo "Token file $TOKEN_FILE already exists; nothing to do."
  exit 0
fi

mkdir -p "$SECRETS_DIR"
# The container runs as uid 1500 and must be able to write the token file.
chmod 777 "$SECRETS_DIR"

output=$(docker run --rm -v "$PWD/$SECRETS_DIR:/out:z" "$IMAGE" \
  influxdb3 create token --admin --offline --name admin --output-file /out/admin-token.json)
chmod 755 "$SECRETS_DIR"

token=$(sed 's/\x1b\[[0-9;]*m//g' <<<"$output" | sed -n 's/^Token: //p')
if [[ -z "$token" ]]; then
  echo "Could not read the generated token from influxdb3 output." >&2
  exit 1
fi

touch .env
chmod 600 .env
{
  echo "INFLUXDB3_TOKEN=$token"
  echo "INFLUXDB3_DATABASE=sensors"
  echo "GRAFANA_ADMIN_PASSWORD=$(openssl rand -base64 18)"
} >> .env

echo "Admin token written to $TOKEN_FILE and .env (keep both out of version control)."
