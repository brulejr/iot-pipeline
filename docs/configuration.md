# Configuration

How the application and its support services are configured: the application's
own settings, machine-specific overrides, and the secrets Docker Compose needs.

## Application settings

Every setting except the PostgreSQL database and credentials has a working default. Override via
environment variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `PROFILE` | `local` | Spring profile; containers run `prod` |
| `SERVER_PORT` | `5001` | HTTP port. Not 8080, which tends to collide locally. |
| `MQTT_URL` | `tcp://localhost:1883` | Broker to subscribe to |
| `MQTT_CLIENT_ID` | `iot-pipeline` | MQTT client identifier |
| `MQTT_TOPIC` | `rtl_433/+/events` | Topic pattern carrying rtl_433 events |
| `PIPELINE_INGEST_MQTT_USERNAME` | — | Broker username, if required |
| `PIPELINE_INGEST_MQTT_PASSWORD` | — | Broker password, if required |
| `POSTGRES_HOST`, `POSTGRES_PORT` | `localhost`, `5432` | PostgreSQL server |
| `POSTGRES_DB` | — (required) | PostgreSQL database, from `.env` |
| `POSTGRES_APP_USERNAME`, `POSTGRES_APP_PASSWORD` | — (required) | The application's PostgreSQL role, from `.env` |
| `STORE_ENABLED` | `true` | `false` runs without storing readings; they are parsed but dropped |
| `MODEL_REGISTRY` | `postgres` | Where models are kept: `postgres` or `memory` |
| `PROMOTION_REGISTRY` | `postgres` | Where device approvals are kept: `postgres` or `memory` |
| `SIGHTING_REGISTRY` | `postgres` | Where sightings of unpromoted devices are kept: `postgres` or `memory` |
| `PUBLISH_MQTT_URL` | `tcp://localhost:1883` | Broker Home Assistant listens on, separate from the ingest one |
| `PUBLISH_MQTT_CLIENT_ID` | `iot-pipeline-publisher` | MQTT client identifier for publishing; must differ from the ingest one |
| `PIPELINE_PUBLISH_MQTT_USERNAME` | — | Publishing broker username, if required |
| `PIPELINE_PUBLISH_MQTT_PASSWORD` | — | Publishing broker password, if required |

Set `pipeline.ingest.mqtt.enabled=false` to start the application without the
MQTT adapter, which is useful when working on later stages without a broker.

Startup fails if PostgreSQL is unreachable while anything uses it, by design: the
schema is migrated before the first reading. To run without a database at all, set
`STORE_ENABLED=false` and each of the three registries to `memory`.

Finer-grained settings — the dedupe window, retention, recommendation thresholds,
Home Assistant prefixes, publication backoff — are under `pipeline.*` in
`src/main/resources/application.yml`, and are described in the guide for each
stage.

## Local overrides

The `local` profile is active by default (`spring.profiles.active:
${PROFILE:local}`), so `src/main/resources/application-local.yml` is read on
every run if it exists. Create it for machine-specific settings; it is
gitignored, so it is a reasonable place for a broker address or credentials you
do not want committed:

```yaml
pipeline:
  ingest:
    mqtt:
      url: tcp://192.168.1.50:1883
      username: pipeline
      password: hunter2
```

A fresh clone has no `application-local.yml` at all, so the defaults in
`application.yml` apply and nothing breaks.

To run from the IDE without exporting `.env` first, import it from
`application-local.yml`; the secrets then stay in one file:

```yaml
spring.config.import: optional:file:.env[.properties]
```

The `[.properties]` hint is needed because `.env` has no extension.

`local` is for development only. Deployments select a different profile with
`PROFILE=<name>` — `prod`, backed by an optional `application-prod.yml` — and
set anything environment-specific through container environment variables,
which outrank every config file.

## Secrets

`scripts/bootstrap.sh` generates `.env`, which is gitignored and must stay that
way. The script is safe to re-run: it only adds what is missing.

Docker Compose reads `.env` from the project root to interpolate the variables
below. The script writes those marked *yes* for you; write them yourself only if
you manage secrets some other way (a vault or password manager). Note that
these reach Compose only — the application's own settings are
[above](#application-settings), and `./gradlew bootRun` does not read `.env`.

| Variable | Written by the script | Purpose |
| --- | --- | --- |
| `COMPOSE_FILE` | yes (`docker/dev/compose.dev.yaml`) | Lets plain `docker compose` find the dev stack |
| `GRAFANA_ADMIN_PASSWORD` | yes (random) | Grafana `admin` password |
| `POSTGRES_DB` | yes (`iot_pipeline`) | Database holding readings, models, approvals and sightings |
| `POSTGRES_ADMIN_PASSWORD` | yes (random) | Password of `postgres`, the instance's superuser, for administration only |
| `POSTGRES_APP_USERNAME` | yes (`iot_pipeline`) | The application's role, which owns the `iot_pipeline` schema |
| `POSTGRES_APP_PASSWORD` | yes (random) | Its password |
| `POSTGRES_GRAFANA_PASSWORD` | yes (random) | Password of `grafana_reader`, Grafana's read-only role |
| `POSTGRES_PORT` | no (defaults to `5432`) | Host port for PostgreSQL — set this if 5432 is already taken |
| `GRAFANA_PORT` | no (defaults to `3000`) | Host port for Grafana |

Compose fails fast with a pointer to the bootstrap script if a secret it needs
is missing. The PostgreSQL roles are created from these passwords on the
container's first start with an empty volume
([`docker/postgres/initdb`](../docker/postgres/initdb/10-roles.sh)); changing one in
`.env` later also needs `ALTER ROLE … PASSWORD` in the database. Running the
application in a container needs broker addresses as well; see
[`docker/local/README.md`](../docker/local/README.md).

Grafana applies `GRAFANA_ADMIN_PASSWORD` only when its data volume is first
created; changing it later has no effect. To bring the stored password back in
line with `.env`:

```bash
docker compose exec grafana grafana cli admin reset-admin-password "<password>"
```

Repeated failed logins lock the `admin` account for about five minutes, and
during the lockout even the right password is refused.
