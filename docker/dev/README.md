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

| Service | URL | Credentials |
| --- | --- | --- |
| InfluxDB 3 | http://localhost:8181 | `INFLUXDB3_TOKEN` in `.env` |
| MongoDB | localhost:27017 | `MONGO_USERNAME` / `MONGO_PASSWORD` in `.env` |
| Grafana | http://localhost:3000 | `admin` / `GRAFANA_ADMIN_PASSWORD` in `.env` |
| InfluxDB 3 Explorer | http://localhost:8888 (loopback only) | none — already connected to InfluxDB |

Host ports can be moved with `INFLUXDB3_PORT`, `MONGO_PORT`, `GRAFANA_PORT` and
`EXPLORER_PORT` in `.env`.

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
