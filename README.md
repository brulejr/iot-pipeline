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
| 1.5 Fingerprint & dedupe | Working — three hashes per reading; repeated deliveries of one transmission are dropped |
| 2. Classification | Working — models identified by structural fingerprint, auto-registered in MongoDB, and recognised once sensor mappings are curated |
| 3. Known/unknown branch | Working — a reading continues only if its device has been promoted |
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
./scripts/bootstrap.sh

# 2. Start InfluxDB 3 and Grafana
docker compose up -d

# 3. Run the application
./gradlew bootRun
```

| Service | URL | Credentials |
| --- | --- | --- |
| Application | http://localhost:5001 | — |
| InfluxDB 3 | http://localhost:8181 | `INFLUXDB3_TOKEN` in `.env` |
| MongoDB | localhost:27017 | `MONGO_USERNAME` / `MONGO_PASSWORD` in `.env` |
| Grafana | http://localhost:3000 | `admin` / `GRAFANA_ADMIN_PASSWORD` in `.env` |

The application needs the MongoDB credentials too, and `.env` is read only by
Compose, so export them before running it:

```bash
set -a; . ./.env; set +a
./gradlew bootRun
```

Without them startup fails naming the missing placeholder, rather than quietly
connecting to whatever else is listening on 27017.

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
| `pipeline.ingest` | DEBUG | Every envelope as it leaves the MQTT adapter, with the raw payload |
| `pipeline.fingerprint` | DEBUG | The event, device and model hashes derived for the reading |
| `pipeline.dedupe` | DEBUG | Readings discarded as duplicates |
| `pipeline.classify` | DEBUG | The model each reading was matched to, and whether it can be parsed |
| `pipeline.promote` | DEBUG | Readings admitted by the gate; WARN for a promoted device whose model is uncurated |
| `pipeline.unknown` | DEBUG | Readings whose device is not promoted, bound for the recommendation engine |
| `pipeline.known` | INFO | Readings from a promoted device |
| `pipeline.errors` | WARN | Failures routed to the ingest error channel |

Each reading produces one line per stage, and each line adds information rather
than repeating the last — the raw payload appears only on the `ingest` line:

```
pipeline.ingest      : received from rtl_433/<host>/events: {"model":"Acurite-Tower","id":3064,...}
pipeline.fingerprint : event=05996555a7aa device=baf1bfc9bc94 model=75ca26ed8cf9
pipeline.classify    : unrecognised Acurite-Tower/A/3064, model Acurite-Tower [75ca26ed8cf9]: model has no curated sensor mappings
pipeline.unknown     : awaiting promotion: Acurite-Tower/A/3064 from rtl_433/<host>/events
```

A reading dropped as a duplicate stops after `pipeline.dedupe`:

```
pipeline.dedupe      : duplicate of device=baf1bfc9bc94 from rtl_433/<host>/events, discarded
```

`pipeline.classify` is where to look for which models are flowing, and which
still need sensor mappings.

Drop `logging.level.pipeline` to INFO once the storage stage lands.
`pipeline.known` stays silent until a device is promoted — expected, not a
fault.

If you see nothing at all, the reading is not reaching the adapter. Check the
broker and topic first, then confirm message counts are rising:

```bash
curl -s localhost:5001/mgmt/metrics/spring.integration.send
```

### Secrets

`scripts/bootstrap.sh` generates `.env` and
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
| `MONGO_USERNAME` | yes (`pipeline`) | MongoDB user, created on first start |
| `MONGO_PASSWORD` | yes (random) | MongoDB password |
| `MONGO_DATABASE` | yes (`iotpipeline`) | Database holding the model registry |
| `INFLUXDB3_PORT` | no (defaults to `8181`) | Host port for InfluxDB |
| `GRAFANA_PORT` | no (defaults to `3000`) | Host port for Grafana |
| `MONGO_PORT` | no (defaults to `27017`) | Host port for MongoDB — set this if 27017 is already taken |

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

### Fingerprinting and model identification

Every reading is fingerprinted right after ingestion, producing three hashes:
the **event** (the reading itself), the **device** (the transmitter), and the
**model** (the payload's structure, with values replaced by the name of their
type). Radio metadata such as `rssi`, `snr` and `time` is excluded from the
event and model hashes, since it describes neither the reading nor the kind of
device that sent it.

**Dedupe** remembers each device's last event hash for `pipeline.dedupe.window`
and drops a repeat, so rtl_433's repeated decodes of one transmission are
counted once.

**Classification** identifies the model from its structural hash rather than
from the self-reported `model` field, so two firmware revisions reporting
different fields are two models under one name. Models register themselves on
first sighting — the catalogue builds from live traffic, with nothing written
out in advance.

Recognising a model takes a human: someone supplies its sensor mappings, saying
how to read values out of its payload. Until then the model is registered but
not recognised, and `pipeline.classify` says so. That curation is the only
hand-maintained input to the stage.

The registry is held in MongoDB behind `ModelRegistryPort`, so discovered
models and their curated mappings survive a restart. Set
`pipeline.model-registry.type=memory` to run without a database, which is what
the tests do.

### Curating a model

List what has been discovered, including each model's structure and whether it
is recognised:

```bash
curl -s localhost:5001/api/models | jq
```

Then curate one, keyed by its structural fingerprint. A curation says what the
model *is* and how to read it, both at once:

```bash
curl -X PUT localhost:5001/api/models/<fingerprint>/curation \
  -H 'Content-Type: application/json' \
  -d '{"category":"weather",
       "sensors":[
        {"name":"temperature_C","type":"ANALOG","classname":"temperature","friendlyName":"Temperature"},
        {"name":"humidity","type":"ANALOG","classname":"humidity","friendlyName":"Humidity"},
        {"name":"battery_ok","type":"BINARY","classname":"battery","friendlyName":"Battery","inverted":true}
      ]}'
```

`category` is what kind of thing the model is — `weather`, `security`, and so on.
It is a free string rather than a closed set, because the band carries new device
types faster than an enum could be extended. A model registers itself from live
traffic, where nobody knows the answer, so it starts as `uncategorised` until
someone curates it.

Each sensor mapping carries:

| Field | Meaning |
| --- | --- |
| `name` | The payload field this mapping reads |
| `type` | `ANALOG` for a measurement, `BINARY` for an on/off state |
| `classname` | Semantic class downstream publishing maps to a consumer's vocabulary |
| `friendlyName` | Optional display name |
| `inverted` | The field's truth is the opposite of what `classname` means to a consumer |

`inverted` exists because rtl_433 and Home Assistant disagree on polarity:
`battery_ok: 1` means the battery is *healthy*, while HA's `battery` class
treats ON as *low*; `closed: 1` means a contact is *shut*, while `opening`
treats ON as *open*. Only the person curating the model knows which way round a
field runs, so it is recorded here rather than guessed at publish time. It
applies to `BINARY` mappings only.

### Promoting a device

Curating a model says how to read that *kind* of device. It does not adopt any
particular one: the antenna hears every transmitter in range, so a reading only
continues past the gate once someone approves the device itself.

Devices are addressed by the key printed on every `pipeline.classify` line —
the source and the id:

```bash
curl -X PUT localhost:5001/api/promoted-devices/rtl433/Acurite-Tower/A/3064 \
  -H 'Content-Type: application/json' \
  -d '{"name":"Back garden sensor","type":"thermometer","area":"garden"}'
```

`name`, `type` and `area` are what the device is to you, as opposed to the
model's `category`, which is what kind of device it is. All three are required.

```bash
curl -s localhost:5001/api/promoted-devices | jq        # what is approved
curl -X DELETE localhost:5001/api/promoted-devices/rtl433/Acurite-Tower/A/3064
```

The gate sends a reading one of three ways:

| Device | Model | Goes to |
| --- | --- | --- |
| Promoted | Curated | `knownDevice` — normal processing |
| Promoted | Uncurated | `promotionGaps` — logged at WARN, since nobody said how to read it |
| Not promoted | Either | `unknownDevice` — the recommendation engine |

Approvals live in MongoDB alongside the model registry, and are restored at
startup from `src/main/resources/promotion-seed.json` the same way curated
mappings are. The file matches `GET /api/promoted-devices`, so a backup is an
export:

```bash
curl -s localhost:5001/api/promoted-devices \
  | jq 'map({deviceKey, name, type, area})' \
  > src/main/resources/promotion-seed.json
```

Seeding only fills gaps: a device the store has no approval for is restored, one
already approved is left alone. A label corrected through the API therefore
survives the next restart rather than being reverted by a stale file. Point
`pipeline.promotion-seed.location` at a `file:` resource to keep the list
outside the repository, or set `pipeline.promotion-seed.enabled=false` to skip
it. An entry with a blank label or device key fails startup, rather than leaving
a device quietly unapproved — which would look identical to one nobody has
reviewed.

### Seeding and backing up curation

Sensor mappings are the only hand-made data in the pipeline; everything else is
derived from live traffic and rebuilds itself. Emptying the store would lose
them, so they are kept in `src/main/resources/model-seed.json`, which is read at
startup.

The file is the same shape as `GET /api/models`, so a backup is an export:

```bash
curl -s localhost:5001/api/models \
  | jq '[.[] | select(.recognised) | {fingerprint, source, name, structure, category, sensors}]' \
  > src/main/resources/model-seed.json
```

Seeding is idempotent and never destructive:

| In the store | What a seed does |
| --- | --- |
| Model absent | Restored whole, mappings included |
| Model present, uncurated | Given the file's mappings |
| Model present, already curated | Left alone |

Curation through the API therefore stays authoritative — a stale seed file
cannot silently revert it. Point `pipeline.model-seed.location` at a `file:`
resource to keep the file outside the repository, or set
`pipeline.model-seed.enabled=false` to skip it.

A seed entry is refused at startup if it has no `category`, if its fingerprint is
not the hash of its own structure, or if a mapping names a field that structure
does not have. Both are
ways a hand-edited file would otherwise produce a model that looks curated but
can never match a reading.

### What curation rejects

A curation must give a real `category` — not blank, and not the `uncategorised`
placeholder. A mapping must name a field the model's structure actually has, at
any depth, and only a `BINARY` mapping may be inverted. A request breaking either rule is
refused whole, leaving existing mappings untouched, and reports every problem
at once rather than one per attempt:

```json
{"message":"bogus: not a field in this model's structure; humidity: inverted applies only to a BINARY mapping",
 "problems":[{"field":"bogus","reason":"not a field in this model's structure"},
             {"field":"humidity","reason":"inverted applies only to a BINARY mapping"}]}
```

Without those checks a typo would store happily, report the model recognised,
and yield nothing at parse time — surfacing in a later stage, far from the
cause.

The model is recognised from the next reading onwards, and `pipeline.classify`
says so. An empty `sensors` list undoes it.

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
