# Production

The application and everything it depends on, on one Docker host: the
application image, PostgreSQL and Grafana. Deployment is done by Ansible,
outside this repository; this directory is what it runs.

## What the deployment does

From a checkout of this repository:

```bash
./gradlew bootJar
docker compose -f docker/prod/compose.prod.yaml --env-file <env file> up -d --build
```

`bootJar` writes `build/libs/iot-pipeline.jar`, which
[`../Dockerfile`](../Dockerfile) packages; the image is tagged
`iot-pipeline:${APP_VERSION:-latest}`. Tests run with `./gradlew build` if the
deployment should refuse a failing commit.

## What it must provide

The env file, kept by Ansible and never committed:

| Variable | Required | |
| --- | --- | --- |
| `INGEST_MQTT_URL` | yes | The broker rtl_433 publishes to |
| `PUBLISH_MQTT_URL` | yes | The broker Home Assistant listens on |
| `POSTGRES_DB` | yes | The database, created on first start; `iot_pipeline` in development |
| `POSTGRES_ADMIN_PASSWORD` | yes | The instance's superuser, `postgres`, for administration only |
| `POSTGRES_APP_USERNAME`, `POSTGRES_APP_PASSWORD` | yes | The application's role, which owns the `iot_pipeline` schema |
| `POSTGRES_GRAFANA_PASSWORD` | yes | Grafana's read-only role, `grafana_reader` |
| `GRAFANA_ADMIN_PASSWORD` | yes | |
| `INGEST_MQTT_CLIENT_ID`, `PUBLISH_MQTT_CLIENT_ID` | no | Default `iot-pipeline` and `iot-pipeline-publisher` |
| `APP_PORT`, `GRAFANA_PORT`, `POSTGRES_PORT` | no | Host ports, default 5001, 3000 and 5432 (PostgreSQL on loopback only) |
| `APP_VERSION` | no | Image tag, default `latest` |

`scripts/bootstrap.sh` shows how the secrets are generated, and can generate them
on the host. The PostgreSQL roles are created from them by
[`../postgres/initdb`](../postgres/initdb/10-roles.sh) on the first start with an
empty volume only: changing a password in the env file afterwards also needs an
`ALTER ROLE … PASSWORD` in the database. Broker credentials, if needed, go in as
`PIPELINE_INGEST_MQTT_USERNAME` / `PIPELINE_INGEST_MQTT_PASSWORD` and
`PIPELINE_PUBLISH_MQTT_USERNAME` / `PIPELINE_PUBLISH_MQTT_PASSWORD` in an
override file adding them to the `app` service's environment.

The `prod` profile reads an `application-prod.yml` if one is on the classpath;
none ships, so the environment is the whole configuration. Variables reach the
application under their full property names (`PIPELINE_*`), which outrank any
profile file.

## Behaviour worth knowing

- **Start-up order.** The application waits for PostgreSQL to report healthy,
  and fails rather than starts if it is unreachable, since it migrates its schema
  before anything is stored. `restart: unless-stopped` retries it.
- **Schema.** The application creates and upgrades its own tables (Flyway, from
  `src/main/resources/db/migration`) in the `iot_pipeline` schema it owns. It
  never runs as the superuser, so the database can later move to a shared
  PostgreSQL by creating the same role and schema there.
- **Retention.** Readings older than `pipeline.store.retention` (30 days) and
  sightings older than a week are deleted by the application as it writes new
  ones.
- **Exposure.** The application (5001) and Grafana (3000) are published on every
  interface; PostgreSQL on loopback only. The application's REST
  API and its console at `/ui` have no authentication, and demoting a device
  deletes it from Home Assistant, so keep 5001 on a trusted network.
- **First start.** A new database restores curated models and promoted devices
  from `model-seed.json` and `promotion-seed.json`, bundled in the jar. Export
  them from the running system before redeploying onto an empty store; see the
  main README.
- **Grafana comes provisioned.** The PostgreSQL datasource and the **Sensors**
  dashboard load from `../grafana/provisioning/`, as in `dev`. Its admin password
  is set from `GRAFANA_ADMIN_PASSWORD` only when the `grafana-data` volume is first
  created.
- **Backups.** Everything the application keeps is in the one database:
  `docker compose exec postgres pg_dump -U postgres "$POSTGRES_DB"` takes it all.
- **Keep in step with `dev`.** The PostgreSQL and Grafana definitions are
  repeated here rather than included, so production can publish fewer ports.
  Image versions should move together.
