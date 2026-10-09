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
| 2. Classification | Working — a curated Kotlin rule set identifies device models and resolves their parse rule set |
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
`http://localhost:5001/mgmt`. Micrometer observations are enabled for every
Spring Integration channel and handler, so each stage is individually
observable.

### Seeing readings

Nothing is persisted yet, so logs are the only record that a reading arrived.
Each stage logs under a `pipeline.*` category, set to DEBUG by default:

| Category | Level | What it shows |
| --- | --- | --- |
| `pipeline.ingest` | DEBUG | Every envelope as it leaves the MQTT adapter |
| `pipeline.unknown` | DEBUG | Readings awaiting promotion, identified or not — currently all of them |
| `pipeline.known` | INFO | Readings from a promoted device |
| `pipeline.errors` | WARN | Failures routed to the ingest error channel |

A healthy stream looks like this, one `ingest` line paired with one `unknown`
line per reading:

```
DEBUG  pipeline.ingest   : received from rtl_433/<host>/events: {"model":"Acurite-Tower",...}
DEBUG  pipeline.unknown  : unpromoted Acurite-Tower/A/3064 [acurite-tower-v1]
                           from rtl_433/<host>/events: {...}
```

A reading whose model is not recognised logs as `unidentified` instead. Drop
`logging.level.pipeline` to INFO once the storage stage lands. `pipeline.known`
stays silent until devices can be promoted — expected, not a fault.

If you see nothing at all, the reading is not reaching the adapter. Check the
broker and topic first, then confirm message counts are rising:

```bash
curl -s localhost:5001/mgmt/metrics/spring.integration.send
```

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

`local` is for development only. Deployments select a different profile with
`PROFILE=<name>` — `prod`, backed by an optional `application-prod.yml` — and
set anything environment-specific through container environment variables,
which outrank every config file.

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
with a default implementation registered via `@ConditionalOnMissingBean`. Real
implementations can be dropped in without touching the flow wiring.

### Classification rules

Classification identifies the device *model*, which resolves the rule set that
parses its payload. It does not decide whether a device is *known* — that is
promotion, a manual step, and nothing is promoted yet.

Rules are type-safe Kotlin, curated by hand in `classify/CuratedRules.kt`:

```kotlin
val CURATED_CLASSIFICATION_RULES: List<ClassificationRule> = classificationRules {
    // rtl_433 reports the model in the payload, so matching it identifies the device.
    rtl433("Acurite-Tower", parseRuleSetId = "acurite-tower-v1", "temperature_C", "humidity")
}
```

The trailing field names are required to be present, so a truncated decode is
not claimed by the rule. For a source that does not self-report a model, use the
general form:

```kotlin
rule("WeatherStation", parseRuleSetId = "weather-station-v1") {
    source("weather-station-rest")
    requireFields("stationId", "observedAt")
    where { it.payload.get("stationId")?.asString()?.startsWith("KBOS") == true }
}
```

Rules evaluate in declaration order and the first match wins, so put narrow
rules before broad ones. Device identity comes from `model`/`channel`/`id`
rather than the receiving antenna, so one transmitter heard by two receivers
yields one key.

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
