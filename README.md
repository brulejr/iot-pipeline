# iot-pipeline

A Kotlin / Spring Integration pipeline for 433 MHz sensor telemetry. An
RTL-SDR dongle running [rtl_433](https://github.com/merbanan/rtl_433) on an
edge host publishes decoded sensor events to MQTT; this application ingests
them, classifies the device that sent each reading, stores data from known
devices in PostgreSQL, and reshapes it for downstream consumers such as Home
Assistant.

The edge host stays deliberately dumb — it only forwards decoded JSON to MQTT.
All routing, classification, and retention logic lives here.

## How it works

```
rtl_433 ─► MQTT ─► ingest ─► fingerprint & dedupe ─► classify ─► promotion gate
                                                                   │        │
                                                          not promoted    promoted
                                                                   │        │
                                                       recommendations    parse ─► PostgreSQL ─► Home Assistant (MQTT)
                                                                                       │
                                                                                    Grafana
```

A 433 MHz antenna hears every transmitter in range — your sensors, the
neighbours', passing cars — so nothing is kept until a person has said so, in two
steps:

1. **Curate the model.** Device models are discovered automatically from the
   structure of their payloads. Curating one says how to read its values: which
   field is the temperature, which the battery state.
2. **Promote the device.** Promotion approves one particular transmitter and
   gives it a name, type and area. The pipeline ranks unpromoted devices by how
   persistently and how strongly they are heard, to suggest which are yours.

Readings from a promoted device of a curated model are stored, and published to
Home Assistant through MQTT discovery. An operator console at
`http://localhost:5001/ui` does all of this from the browser, including a live
monitor for identifying a device by exercising it.

## Status

Stages 1 to 7 work end to end, from ingestion to publication for Home Assistant;
alerting is not started.

| Stage | State |
| --- | --- |
| 1. Ingestion (MQTT) | Working |
| 1.5 Fingerprint & dedupe | Working — three hashes per reading; repeated deliveries of one transmission are dropped |
| 2. Classification | Working — models identified by structural fingerprint, auto-registered in PostgreSQL, and recognised once sensor mappings are curated |
| 3. Known/unknown branch | Working — a reading continues only if its device has been promoted |
| 4. Recommendation engine | Working — ranks unpromoted devices by how persistently and how nearby they are heard |
| 5. Storage (PostgreSQL) | Working — parsed readings are written, and deleted after a retention period |
| 6. Transformation | Working — stored readings are shaped for Home Assistant's MQTT discovery |
| 7. Publication | Working — published to the consumer's broker; failures are held, latest per topic, and retried |
| 8. Alerting | Not started |

## Requirements

- JDK 21 (the Gradle toolchain will resolve one if it is not your default)
- Docker with Compose, for PostgreSQL and Grafana
- An MQTT broker, and an rtl_433 feed publishing to it

Gradle itself does not need to be installed — use the bundled wrapper.

## Getting started

Both ways start with `./scripts/bootstrap.sh`, which generates the database and
Grafana passwords into a gitignored `.env` and is safe to re-run.

### Local deployment

For development and trying it out: PostgreSQL and Grafana in Docker, the
application run with Gradle or from the IDE.

```bash
# 1. Generate the passwords and write .env (first run only)
./scripts/bootstrap.sh

# 2. Start the support services: PostgreSQL and Grafana.
#    bootstrap.sh set COMPOSE_FILE in .env, which points this at docker/dev.
docker compose up -d

# 3. Export the PostgreSQL credentials (only Compose reads .env), then run
set -a; . ./.env; set +a
./gradlew bootRun
```

Without the exported credentials startup fails naming the missing placeholder,
rather than quietly connecting to whatever else is listening on 5432.

| Service | URL | Credentials |
| --- | --- | --- |
| Application and console | http://localhost:5001/ui | — |
| PostgreSQL | localhost:5432, database `iot_pipeline` | `POSTGRES_APP_USERNAME` / `POSTGRES_APP_PASSWORD` in `.env` |
| Grafana | http://localhost:3000 | `admin` / `GRAFANA_ADMIN_PASSWORD` in `.env` |

The application subscribes to `rtl_433/+/events` on `tcp://localhost:1883` by
default, and publishes for Home Assistant to the same address. Point them at your
brokers with `MQTT_URL` and `PUBLISH_MQTT_URL`, or in a gitignored
`application-local.yml`; see [Configuration](docs/configuration.md).

To run the application in a container on this machine too, the way production
runs it, see [`docker/local`](docker/local/README.md).

### Production deployment

Everything on one Docker host: the application image, PostgreSQL and Grafana. On
that host, from a checkout of this repository:

```bash
# 1. Generate the passwords and write .env (first run only)
./scripts/bootstrap.sh

# 2. Name the brokers; production has no defaults for them
cat >> .env <<'EOF'
INGEST_MQTT_URL=tcp://<broker rtl_433 publishes to>:1883
PUBLISH_MQTT_URL=tcp://<broker Home Assistant listens on>:1883
EOF

# 3. Build the jar, then the image, and start everything
./gradlew bootJar
docker compose -f docker/prod/compose.prod.yaml --env-file .env up -d --build
```

The application is on port 5001 and Grafana on 3000; PostgreSQL listens on
loopback only. The application waits for PostgreSQL to be healthy, migrates its
own schema, and restores curated models and promoted devices from the seed files
bundled in the jar. Keep port 5001 on a trusted network.

[`docker/prod`](docker/prod/README.md) lists every variable the env file can set,
including broker credentials and host ports, and covers backups and what
happens on first start.

## Usage

Once readings are flowing:

1. **Find your devices.** Open the console's **Recommendations** page, or the
   **Live monitor** filtered to unpromoted devices, and trigger a sensor — open
   the door, press the button — to see which transmission is yours.
   See [Operator console](docs/console.md).
2. **Curate its model**, if it is not yet recognised, on the **Models** page:
   map each payload field to what it measures. See
   [Models and curation](docs/models.md).
3. **Promote the device** with a name, type and area. See
   [Devices](docs/devices.md).

From then on its readings are stored in PostgreSQL, shown on Grafana's
provisioned **Sensors** dashboard ([Stored readings](docs/readings.md)), and
appear in Home Assistant as a device with an entity per sensor
([Home Assistant](docs/home-assistant.md)).

Everything the console does is also available from the REST API, for example:

```bash
curl -s localhost:5001/api/recommendations | jq
curl -X PUT localhost:5001/api/promoted-devices/rtl433/Acurite-Tower/A/3064 \
  -H 'Content-Type: application/json' \
  -d '{"name":"Back garden sensor","type":"thermometer","area":"garden"}'
```

The console and API have no authentication; keep port 5001 on a trusted network.

## Documentation

| Guide | Covers |
| --- | --- |
| [Operator console](docs/console.md) | The `/ui` pages and the live monitor |
| [Models and curation](docs/models.md) | Model discovery, sensor mappings, seeding and backups |
| [Devices](docs/devices.md) | Recommendations, promotion, and the promotion gate |
| [Stored readings](docs/readings.md) | The `readings` table, retention, Grafana and SQL |
| [Home Assistant](docs/home-assistant.md) | Discovery, state messages, and publication retries |
| [Configuration](docs/configuration.md) | Environment variables, local overrides, secrets |
| [Operations](docs/operations.md) | Actuator, the REST API, logs, and troubleshooting |
| [Docker](docker/README.md) | Running in containers, locally or in production |
| [Development](docs/development.md) | Building, testing, and the source layout |
| [Design](docs/DESIGN.md) | How it is built and why: principles, each stage, open questions |

## Development

```bash
./gradlew build      # compile and test
```

Built with Kotlin and Spring Boot on a Java 21 toolchain. See
[Development](docs/development.md) for the layout and the pitfalls to watch for,
and [`docs/DESIGN.md`](docs/DESIGN.md) before changing the pipeline's structure.

## License

MIT — see [LICENSE](LICENSE).
