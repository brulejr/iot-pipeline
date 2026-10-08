# iot-pipeline

A Kotlin / Spring Integration pipeline for 433 MHz sensor telemetry. An
RTL-SDR dongle running [rtl_433](https://github.com/merbanan/rtl_433) on an
edge host publishes decoded sensor events to MQTT; this application ingests
them, classifies the device that sent each reading, stores data from known
devices in InfluxDB 3, and reshapes it for downstream consumers such as Home
Assistant.

The edge host stays deliberately dumb — it only forwards decoded JSON to MQTT.
All routing, classification, and retention logic lives here.

## Status

Early scaffold. Ingestion works end to end; most of the pipeline does not yet.

| Stage | State |
| --- | --- |
| 1. Ingestion (MQTT) | Working |
| 2. Classification | Port defined; `NoRulesClassifier` marks every reading unknown, keyed by the rtl_433 `model`/`channel`/`id` fields |
| 3. Known/unknown branch | Stub |
| 4. Recommendation engine | Stub |
| 5. Storage (InfluxDB) | Stub — the container runs, but nothing writes to it yet |
| 6. Transformation | Stub |
| 7. Publication | Stub |
| 8. Alerting | Not started |

Stubs live in `pipeline/StageStubFlows.kt` and log whatever reaches them.
[`docs/DESIGN.md`](docs/DESIGN.md) is the authoritative design document and
covers the reasoning behind each stage.

## Requirements

- JDK 21 (the Gradle toolchain will resolve one if it is not your default)
- Docker with Compose, for InfluxDB and Grafana
- An MQTT broker, and an rtl_433 feed publishing to it

Gradle itself does not need to be installed — use the bundled wrapper.

## Getting started

```bash
# 1. Mint the InfluxDB admin token and write .env (first run only)
./scripts/bootstrap-influxdb.sh

# 2. Start InfluxDB 3 and Grafana
docker compose up -d

# 3. Run the application
./gradlew bootRun
```

| Service | URL | Credentials |
| --- | --- | --- |
| Application | http://localhost:5001 | — |
| InfluxDB 3 | http://localhost:8181 | `INFLUXDB3_TOKEN` in `.env` |
| Grafana | http://localhost:3000 | `admin` / `GRAFANA_ADMIN_PASSWORD` in `.env` |

Grafana is provisioned with the InfluxDB datasource already wired up, and is
treated as an external read-only consumer — the application has no Grafana
integration of its own.

Actuator exposes `health`, `info`, `metrics`, and `integrationgraph` under
`http://localhost:5001/actuator`. Micrometer observations are enabled for every
Spring Integration channel and handler, so each stage is individually
observable.

### Secrets

`scripts/bootstrap-influxdb.sh` generates `.env` and
`docker/influxdb3/secrets/admin-token.json`. Both are gitignored and must stay
that way; the script is safe to re-run and does nothing if the token already
exists.

Docker Compose reads `.env` from the project root to interpolate the variables
below. The script writes the first three for you; write them yourself only if
you manage secrets some other way (a vault or password manager). Note that
these reach Compose only — the application's own settings are under
[Configuration](#configuration) below, and `./gradlew bootRun` does not read
`.env`.

| Variable | Written by the script | Purpose |
| --- | --- | --- |
| `INFLUXDB3_TOKEN` | yes | InfluxDB admin token; Grafana authenticates with it |
| `INFLUXDB3_DATABASE` | yes (`sensors`) | Database the Grafana datasource queries |
| `GRAFANA_ADMIN_PASSWORD` | yes (random) | Grafana `admin` password |
| `INFLUXDB3_PORT` | no (defaults to `8181`) | Host port for InfluxDB |
| `GRAFANA_PORT` | no (defaults to `3000`) | Host port for Grafana |

Compose fails fast with a pointer to the bootstrap script if the first three
are missing.

## Configuration

Every setting has a working default, so the application starts with no
configuration at all. Override via environment variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `5001` | HTTP port. Not 8080, which tends to collide locally. |
| `MQTT_URL` | `tcp://localhost:1883` | Broker to subscribe to |
| `MQTT_CLIENT_ID` | `iot-pipeline` | MQTT client identifier |
| `MQTT_TOPIC` | `rtl_433/+/events` | Topic pattern carrying rtl_433 events |
| `PIPELINE_INGEST_MQTT_USERNAME` | — | Broker username, if required |
| `PIPELINE_INGEST_MQTT_PASSWORD` | — | Broker password, if required |

Set `pipeline.ingest.mqtt.enabled=false` to start the application without the
MQTT adapter, which is useful when working on later stages without a broker.

### Local overrides

For machine-specific settings, create
`src/main/resources/application-local.yml` and run with the `local` profile
active:

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

Anything in that file overrides `application.yml`, and it is gitignored, so it
is a reasonable place for a broker address or credentials you do not want
committed:

```yaml
pipeline:
  ingest:
    mqtt:
      url: tcp://192.168.1.50:1883
      username: pipeline
      password: hunter2
```

The profile is not active by default — without `SPRING_PROFILES_ACTIVE` the
file is ignored entirely, so it cannot change behaviour for anyone who has not
opted in.

## Layout

```
src/main/kotlin/io/brulejr/iotpipeline/
├── IotPipelineApplication.kt
├── ingest/           # Stage 1 — transport adapters
│   └── mqtt/         #   MQTT/Paho adapter
├── classify/         # Stage 2 — device classification
└── pipeline/         # Channel definitions and stage stubs
```

Packages mirror pipeline stages. `pipeline/PipelineChannels.kt` holds the
channel names that wire stages together; every ingestion adapter, whatever its
transport, ends by publishing a `SensorEnvelope` to `PipelineChannels.INGEST`,
so later stages never see transport details.

Each stage sits behind a port interface — `ClassificationPort`, for example —
with a default no-op implementation registered via `@ConditionalOnMissingBean`.
Real implementations can be dropped in without touching the flow wiring.

## Development

```bash
./gradlew build      # compile and test
./gradlew test       # tests only
./gradlew bootRun    # run locally
```

Built with Kotlin 2.3.21 and Spring Boot 4.1.1 on a Java 21 toolchain. Two
things to watch for when adding code:

- Jackson is the 3.x `tools.jackson` namespace, **not** `com.fasterxml.jackson`.
- Eclipse Paho must stay an explicit dependency; it is optional in
  `spring-integration-mqtt` and will not resolve transitively.

Project conventions for Claude Code are in [`CLAUDE.md`](CLAUDE.md) and
[`.claude/CONVENTIONS.md`](.claude/CONVENTIONS.md).

## License

MIT — see [LICENSE](LICENSE).
