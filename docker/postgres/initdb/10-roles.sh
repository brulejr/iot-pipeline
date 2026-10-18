#!/usr/bin/env bash
# Runs once, when the PostgreSQL container starts on an empty data volume, as the
# instance's superuser. Creates the application's own role and schema, and a read-only
# role for Grafana. The tables themselves are created by the application's migrations.
#
# The application owns its schema rather than running as the superuser, so the
# database can later move to a shared instance without granting it more than its own
# tables. Grafana reads them through a role that can only SELECT: its query editor
# runs whatever SQL a dashboard holds.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v app_user="$POSTGRES_APP_USERNAME" \
  -v app_password="$POSTGRES_APP_PASSWORD" \
  -v grafana_password="$POSTGRES_GRAFANA_PASSWORD" \
  -v schema=iot_pipeline <<'SQL'
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password';
CREATE SCHEMA :"schema" AUTHORIZATION :"app_user";

CREATE ROLE grafana_reader LOGIN PASSWORD :'grafana_password';
GRANT USAGE ON SCHEMA :"schema" TO grafana_reader;
-- Tables the application creates later are readable as soon as they exist.
ALTER DEFAULT PRIVILEGES FOR ROLE :"app_user" IN SCHEMA :"schema" GRANT SELECT ON TABLES TO grafana_reader;
-- So dashboard SQL can name tables without the schema.
ALTER ROLE grafana_reader SET search_path = :"schema";
SQL
