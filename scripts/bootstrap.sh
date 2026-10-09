#!/usr/bin/env bash
# Provisions local secrets into .env: an offline InfluxDB 3 admin token (loaded by the
# server at startup via --admin-token-file), a Grafana admin password, and MongoDB
# credentials. Safe to re-run: each secret is generated only if it is missing, so
# running this after adding a new service fills in just the new keys.
set -euo pipefail

cd "$(dirname "$0")/.."

SECRETS_DIR=docker/influxdb3/secrets
TOKEN_FILE="$SECRETS_DIR/admin-token.json"
INFLUX_IMAGE=influxdb:3.12-core

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

echo "InfluxDB:"
if [[ -e "$TOKEN_FILE" ]]; then
  echo "  $TOKEN_FILE already exists, left alone"
else
  mkdir -p "$SECRETS_DIR"
  # The container runs as uid 1500 and must be able to write the token file.
  chmod 777 "$SECRETS_DIR"
  output=$(docker run --rm -v "$PWD/$SECRETS_DIR:/out:z" "$INFLUX_IMAGE" \
    influxdb3 create token --admin --offline --name admin --output-file /out/admin-token.json)
  chmod 755 "$SECRETS_DIR"

  token=$(sed 's/\x1b\[[0-9;]*m//g' <<<"$output" | sed -n 's/^Token: //p')
  if [[ -z "$token" ]]; then
    echo "Could not read the generated token from influxdb3 output." >&2
    exit 1
  fi
  ensure_env INFLUXDB3_TOKEN "$token"
  echo "  admin token written to $TOKEN_FILE"
fi
ensure_env INFLUXDB3_DATABASE sensors

echo "Grafana:"
ensure_env GRAFANA_ADMIN_PASSWORD "$(openssl rand -base64 18)"

echo "MongoDB:"
ensure_env MONGO_USERNAME pipeline
ensure_env MONGO_PASSWORD "$(openssl rand -base64 18 | tr -d '/+=')"
ensure_env MONGO_DATABASE iotpipeline

echo
echo "Secrets are in .env and $SECRETS_DIR; both are gitignored. Keep them out of version control."
