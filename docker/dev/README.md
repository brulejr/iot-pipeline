# Development

The support services the application needs while it runs in the IDE. The
application itself is not containerised here; see [`../local`](../local/README.md)
for that.

```bash
# First run only: mint the InfluxDB admin token and write .env
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
docker compose logs -f influxdb3
```

The path is relative, so run them from the repository root. An explicit `-f`
still wins, which is how [`local`](../local/README.md) is run.

| Service | URL | Credentials |
| --- | --- | --- |
| InfluxDB 3 | http://localhost:8181 | `INFLUXDB3_TOKEN` in `.env` |
| MongoDB | localhost:27017 | `MONGO_USERNAME` / `MONGO_PASSWORD` in `.env` |
| Grafana | http://localhost:3000 | `admin` / `GRAFANA_ADMIN_PASSWORD` in `.env` |
| InfluxDB 3 Explorer | http://localhost:8888 (loopback only) | none — already connected to InfluxDB |

Host ports can be moved with `INFLUXDB3_PORT`, `MONGO_PORT`, `GRAFANA_PORT` and
`EXPLORER_PORT` in `.env`.

Grafana is provisioned with the InfluxDB data source and a **Sensors** dashboard
(folder *IoT Pipeline*): current values, temperature and humidity over time,
battery state, and when each device was last heard. Both live in
`docker/grafana/provisioning/`, shared with `prod`. The dashboard is read-only in
Grafana; edit `dashboards/iot-pipeline/sensors.json` instead, and Grafana reloads
it within 30 seconds. A new provisioning file needs a Grafana restart. InfluxDB 3
Core refuses queries that span too many stored files, so time ranges beyond a few
days may fail.

Explorer runs in read-only `query` mode, since the application owns the database
and its retention period; set `EXPLORER_MODE=admin` in `.env` for database and
token management. It listens on loopback only because it holds the admin token
and has no login of its own. From another machine, tunnel to it:

```bash
ssh -L 8888:localhost:8888 <this host>
```

The application run from the IDE reads its settings from
`src/main/resources/application-local.yml` (gitignored) and needs the MongoDB
credentials from `.env`. Compose reads `.env`; the application does not, unless
`application-local.yml` imports it or they are exported first:

```bash
set -a; . ./.env; set +a
./gradlew bootRun
```
