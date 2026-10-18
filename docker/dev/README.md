# Development

The support services the application needs while it runs in the IDE. The
application itself is not containerised here; see [`../local`](../local/README.md)
for that.

```bash
# First run only: generate the passwords and write .env
./scripts/bootstrap.sh

# From the repository root
docker compose -f docker/dev/compose.dev.yaml --env-file .env up -d
```

`--env-file .env` is needed because Compose otherwise looks for `.env` beside the
compose file, in `docker/dev/`.

### Shorter commands

`scripts/bootstrap.sh` adds this line to `.env` (add it by hand to a `.env` made
before it did):

```
COMPOSE_FILE=docker/dev/compose.dev.yaml
```

Compose reads `COMPOSE_FILE` from the `.env` in the directory it runs in and then
takes the other variables from that same file, so from the repository root the
plain commands work:

```bash
docker compose up -d
docker compose ps
docker compose logs -f postgres
```

The path is relative, so run them from the repository root. An explicit `-f`
still wins, which is how [`local`](../local/README.md) is run.

| Service | URL | Credentials |
| --- | --- | --- |
| PostgreSQL | localhost:5432, database `POSTGRES_DB` | `POSTGRES_APP_USERNAME` / `POSTGRES_APP_PASSWORD` in `.env` |
| Grafana | http://localhost:3000 | `admin` / `GRAFANA_ADMIN_PASSWORD` in `.env` |

Host ports can be moved with `POSTGRES_PORT` and `GRAFANA_PORT` in `.env`.

PostgreSQL holds everything the application keeps, in the `iot_pipeline` schema:
`models`, `promoted_devices`, `sightings` and `readings`. On the first start with
an empty volume, [`../postgres/initdb`](../postgres/initdb/10-roles.sh) creates
three roles: `postgres`, the superuser (`POSTGRES_ADMIN_PASSWORD`); the
application's own role, which owns the schema; and `grafana_reader`, which can
only read it. The tables are created by the application the first time it starts.
To query them:

```bash
docker compose exec postgres psql -U postgres -d iot_pipeline -c '\dt iot_pipeline.*'
```

Grafana is provisioned with the PostgreSQL data source and a **Sensors** dashboard
(folder *IoT Pipeline*): current values, temperature and humidity over time,
battery state, and when each device was last heard. Both live in
`docker/grafana/provisioning/`, shared with `prod`. The dashboard is read-only in
Grafana; edit `dashboards/iot-pipeline/sensors.json` instead, and Grafana reloads
it within 30 seconds. A new provisioning file needs a Grafana restart.

The application run from the IDE reads its settings from
`src/main/resources/application-local.yml` (gitignored) and needs the PostgreSQL
credentials from `.env`. Compose reads `.env`; the application does not, unless
`application-local.yml` imports it or they are exported first:

```bash
set -a; . ./.env; set +a
./gradlew bootRun
```
