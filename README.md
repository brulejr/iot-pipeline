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
| 4. Recommendation engine | Working — ranks unpromoted devices by how persistently and how nearby they are heard |
| 5. Storage (InfluxDB) | Working — parsed readings are written with a retention period |
| 6. Transformation | Working — stored readings are shaped for Home Assistant's MQTT discovery |
| 7. Publication | Working — published to the consumer's broker; failures are held, latest per topic, and retried |
| 8. Alerting | Not started |

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

# 2. Start the support services: InfluxDB 3, MongoDB, Grafana, Explorer.
#    bootstrap.sh set COMPOSE_FILE in .env, which points this at docker/dev.
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
| InfluxDB 3 Explorer | http://localhost:8888 | none — already connected to InfluxDB |

The application needs the MongoDB credentials too, and `.env` is read only by
Compose, so export them before running it:

```bash
set -a; . ./.env; set +a
./gradlew bootRun
```

Without them startup fails naming the missing placeholder, rather than quietly
connecting to whatever else is listening on 27017.

That is development, with the application in the IDE. [`docker/`](docker/README.md)
also covers running the application itself in a container — locally alongside
these services, or in production — each with its own README.

Grafana is provisioned with the InfluxDB datasource already wired up, and is
treated as an external read-only consumer — the application has no Grafana
integration of its own.

Actuator exposes `health`, `info`, `metrics`, and `integrationgraph` under
`http://localhost:5001/mgmt`. Micrometer observations are enabled for every
Spring Integration channel and handler, so each stage is individually
observable.

Every feature contributes to both `health` and `info`. Health reports whether
the feature's backing store answers, with counts since startup: the MQTT
connection is down once the broker is lost, and storage is down when InfluxDB
cannot be reached. Info shows each feature's settings, minus credentials.
Each feature also has an endpoint of its own:

| Endpoint | What it gives |
| --- | --- |
| `GET /api/ingest/mqtt` | Broker connection, messages received and malformed |
| `POST /api/fingerprints` | The hashes a payload would get, without processing it |
| `GET /api/dedupe` | Readings passed and discarded as duplicates |
| `GET /api/models`, `PUT /api/models/{fingerprint}/curation` | The model registry and curation |
| `GET /api/promoted-devices`, `PUT`/`DELETE …/{source}/{deviceId}` | Device approvals |
| `GET /api/recommendations` | Unpromoted devices, best candidate first |
| `POST /api/models/{fingerprint}/parse` | What a payload would parse to under a model's curation |
| `GET /api/store` | Readings and values written, and failed writes |
| `GET /api/outbound-messages` | The latest Home Assistant message for each topic |
| `GET /api/publish`, `GET /api/publish/pending`, `POST /api/publish/flush` | Publication counts, messages held for retry, and a retry now |

### Seeing readings

Logs follow a reading through every stage, including the ones that drop it.
Each stage logs under a `pipeline.*` category, set to DEBUG by default:

| Category | Level | What it shows |
| --- | --- | --- |
| `pipeline.ingest` | DEBUG | Every envelope as it leaves the MQTT adapter, with the raw payload |
| `pipeline.fingerprint` | DEBUG | The event, device and model hashes derived for the reading |
| `pipeline.dedupe` | DEBUG | Readings discarded as duplicates |
| `pipeline.classify` | DEBUG | The model each reading was matched to, and whether it can be parsed |
| `pipeline.promote` | DEBUG | Readings admitted by the gate; WARN for a promoted device whose model is uncurated |
| `pipeline.unknown` | DEBUG | Readings whose device is not promoted, counted by the recommendation engine |
| `pipeline.known` | INFO | Readings from a promoted device |
| `pipeline.parse` | INFO | Values read out of a payload through its model's mappings; WARN for an incomplete reading dropped by `pipeline.parse.drop-incomplete` |
| `pipeline.store` | DEBUG | Readings written to InfluxDB |
| `pipeline.transform` | DEBUG | Messages shaped for Home Assistant, with their topics |
| `pipeline.publish` | DEBUG | Each message published; WARN when the broker fails, INFO when held messages are delivered |
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
| `EXPLORER_SESSION_SECRET` | yes (random) | Keeps Explorer sessions valid across restarts |
| `EXPLORER_PORT` | no (defaults to `8888`) | Host port for InfluxDB 3 Explorer |
| `EXPLORER_MODE` | no (defaults to `query`) | `admin` adds database and token management to Explorer |

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
| `MODEL_REGISTRY` | `mongo` | Where models are kept: `mongo` or `memory` |
| `PROMOTION_REGISTRY` | `mongo` | Where device approvals are kept: `mongo` or `memory` |
| `SIGHTING_REGISTRY` | `mongo` | Where sightings of unpromoted devices are kept: `mongo` or `memory` |
| `PUBLISH_MQTT_URL` | `tcp://localhost:1883` | Broker Home Assistant listens on, separate from the ingest one |
| `PUBLISH_MQTT_CLIENT_ID` | `iot-pipeline-publisher` | MQTT client identifier for publishing; must differ from the ingest one |
| `PIPELINE_PUBLISH_MQTT_USERNAME` | — | Publishing broker username, if required |
| `PIPELINE_PUBLISH_MQTT_PASSWORD` | — | Publishing broker password, if required |

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

## Layout

```
src/main/kotlin/io/brulejr/iotpipeline/
├── IotPipelineApplication.kt
├── ingest/mqtt/      # Stage 1 — the MQTT/Paho adapter
├── fingerprint/      #   event, device and model hashes
├── dedupe/           #   repeated decodes dropped
├── classify/         # Stage 2 — model identification and curation
├── promote/          # Stage 3 — the promotion gate
├── recommend/        # Stage 4 — ranking unpromoted devices
├── parse/            # Stage 5 — values read through sensor mappings
├── store/            #   InfluxDB storage
├── transform/        # Stage 6 — shaping for Home Assistant
├── publish/          # Stage 7 — delivery to the consumer's broker
└── pipeline/         # Cross-cutting: channels, error log, registry choice
```

Packages mirror pipeline stages. `pipeline/PipelineChannels.kt` holds the
channel names that wire stages together; every ingestion adapter, whatever its
transport, ends by publishing a `SensorEnvelope` to `PipelineChannels.INGEST`,
so later stages never see transport details.

Every feature package has the same shape: one `*Config` declaring its beans
and flows, a validated `*Datafill` for its `pipeline.*` settings, a `*Service`
holding its logic, a `*Controller`, a health indicator and an info
contributor. Storage sits behind ports such as `ModelRegistryPort`, so MongoDB
and in-memory implementations swap without touching the flow wiring.

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
`pipeline.classification.registry=memory` to run without a database, which is what
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

### Querying InfluxDB

**InfluxDB 3 Explorer** at http://localhost:8888 is InfluxData's own UI — a
schema browser and SQL editor, much like MongoDB Compass. It arrives connected
to the readings database; nothing to configure.

It runs in read-only `query` mode, since the application owns the database and
its retention period. Set `EXPLORER_MODE=admin` in `.env` for database and token
management. It is published on localhost only: Explorer has no login but holds
the admin token, so on every interface it would hand the whole database to
anyone on the network. From another machine, use an SSH tunnel:

```bash
ssh -L 8888:localhost:8888 <this host>
```

**Grafana** at http://localhost:3000 is provisioned against the same database:
*Explore* → *InfluxDB 3* → *Code* runs SQL and charts the result, which is where
dashboards come from.

From the command line:

```bash
set -a; . ./.env; set +a
docker compose exec influxdb3 influxdb3 query --database sensors --token "$INFLUXDB3_TOKEN" \
  "SELECT time, device_name, sensor, value, state FROM reading ORDER BY time DESC LIMIT 10"
```

### Stored readings

A promoted device's readings are parsed through its model's mappings and written
to InfluxDB, one point per value:

```sql
SELECT time, device_name, device_area, category, model, sensor, value, state
FROM reading ORDER BY time DESC
```

| Tag | |
| --- | --- |
| `source`, `device` | The device key, as the gate and classifier print it |
| `model`, `category` | What kind of device it is, from the model |
| `sensor` | The mapping's `classname` — `temperature`, `battery`, and so on |

| Field | |
| --- | --- |
| `value` / `state` | The reading: `value` for an analog one, `state` for a binary one |
| `device_name`, `device_type`, `device_area` | What the device was to you when the reading was taken, from its promotion |

Tags hold only what cannot be relabelled, so a device's history stays one series
when you rename or move it: filter and group on `device`, and read the labels
from the fields. `value` and `state` are separate because InfluxDB fixes a
field's type on first write, so one field could not hold both.

Readings written before 2026-10-14 carry the labels as tags named `name`,
`type` and `area` instead; InfluxDB will not reuse a tag's name for a field,
hence the `device_` prefix. Until those rows age out under the retention period,
`COALESCE(device_name, name)` reads either. One point per value rather than
per reading means each sensor is a series in its own right, so a model gaining
or losing a mapping does not reshape what is already stored.

The application creates the database itself, with
`pipeline.store.retention` (30d by default). This matters: InfluxDB creates a
database implicitly on the first write, and an implicitly created one has **no
retention at all**, so leaving it to the first write would keep readings for
ever. A retention period is fixed at creation, so changing the setting
afterwards does not alter an existing database — the log says so when it finds
one. Set `pipeline.store.enabled=false` to run the pipeline without writing
anything; readings are still parsed and logged.

### Recommendations

The antenna hears every transmitter in range — yours, the neighbours', passing
cars. Every reading from a device nobody has promoted is counted, by hour, and
the devices are ranked for review:

```bash
curl -s localhost:5001/api/recommendations | jq
```

| Field | |
| --- | --- |
| `persistence` | Share of the last 24 hours the device was heard in, 0..1 |
| `proximity` | Median SNR placed between 5 and 17 dB, 0..1; 0.5 if no signal |
| `score` | 0.6 × proximity + 0.4 × persistence; the list is ordered by it |
| `recommended` | Heard in at least 3 of the last 24 hours, with proximity of at least 0.5 (about 11 dB) |
| `modelCurated` | Whether its model has sensor mappings; promote an uncurated one and it reaches the gate only to be reported as a gap |

**Persistence, not rate.** A door sensor that checks in once an hour is around
all day; a car parked nearby for half an hour sends hundreds of readings and is
gone. Counting the hours a device is heard in tells those apart where a count of
readings would not. Hours are clock hours, so three of them means a device has
been around for more than an hour; two would be satisfied by readings a minute
apart either side of the hour.

**Near as well as persistent.** A neighbour's sensor is around all day too, so
time alone would flag it. A device is only recommended once its signal is
strong enough as well. A device whose readings carry no signal is judged on
hours alone.

You do not have to wait for the flag: every device heard in the window is
listed straight away, ranked, and can be promoted at any time. `recommended`
only marks the ones worth looking at first.

**Proximity from SNR, not RSSI.** rtl_433 reports RSSI relative to full scale,
so strong signals all read close to zero. SNR spreads near and far apart and does
not depend on the radio's gain.

Nothing is promoted automatically. Promote a candidate with its key:

```bash
curl -X PUT localhost:5001/api/promoted-devices/rtl433/DSC-Security/2321243 \
  -H 'Content-Type: application/json' \
  -d '{"name":"Front door","type":"contact","area":"hallway"}'
```

Sightings are kept for a week, in MongoDB's `sightings` collection, and expire
on their own. The thresholds are in `application.yml` under
`pipeline.recommendation`; they were calibrated on few samples and are worth
revisiting as more devices are seen.

### Home Assistant messages

After a reading is stored it is shaped for Home Assistant's MQTT discovery and
published to the broker Home Assistant listens on (`PUBLISH_MQTT_URL`). Two
kinds of message, both visible on `pipeline.transform`:

| Message | Topic | Retained |
| --- | --- | --- |
| Discovery: the device and an entity per sensor mapping | `homeassistant/device/<id>/config` | always |
| State: one per reading, values keyed by payload field | `iot-pipeline/<id>/state` | `retain-state`, default yes |

`<id>` comes from the device key — `rtl433_acurite_tower_a_3064` — never from the
name you gave it, which is free text: Home Assistant ids allow only letters,
digits, `_` and `-`. The name appears as the device's display name instead, and
the area as its suggested area.

Discovery is sent only when it changes: once after startup, and again when a
curation alters what the device reports. Each analog mapping becomes a `sensor`
with `state_class: measurement`, so Home Assistant keeps long-term statistics
for it; each binary mapping becomes a `binary_sensor` reporting plain `ON` and
`OFF`, the mapping's `inverted` flag having been applied when the payload was
parsed.

Units are read from rtl_433's field names, which carry them: `temperature_C` is
°C, `temperature_F` °F, `pressure_hPa` hPa, `wind_avg_km_h` km/h, `humidity` %.
A field whose name carries no unit is sent without one.

Prefixes and retention are under `pipeline.transform.home-assistant` in
`application.yml`.

Publication is on by default; set `pipeline.publish.enabled=false` to shape
messages without sending them. When the broker cannot be reached, nothing is
lost that matters: the failed message is held, and so is everything after it,
one per topic — a newer state replaces the one waiting, since Home Assistant
only wants the latest and every reading is in InfluxDB anyway. Held messages
are retried in arrival order after a backoff that doubles from
`pipeline.publish.retry.initial-backoff` up to `max-backoff`, and health reports
`publish` DOWN until they are delivered. `GET /api/publish/pending` shows what
is waiting; `POST /api/publish/flush` retries at once. Held messages are kept in
memory, so a restart drops them, but discovery is sent again after a restart and
the next reading brings fresh state.

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

Demoting a device also removes it from Home Assistant: an empty retained message
goes to its discovery topic, which Home Assistant treats as deleting the device,
and another to its state topic, so the broker stops keeping its last reading.
Both go through publication, so a broker outage holds them like any other
message. Promoting the device again recreates it with its next reading.

To relabel a device, promote it again with the new `name`, `type` or `area`.
Its next reading sends Home Assistant updated discovery, and the change shows
up on the existing device — with one exception. Home Assistant uses
`suggested_area` only when it creates a device, so a changed area is ignored
for one it already has. Either set the area in Home Assistant, which keeps the
device and its history as they are, or demote and promote the device, so Home
Assistant deletes it and creates it again in the new area. That costs the
readings that arrive between the two calls, and may briefly break the device's
graphs in Home Assistant. Stored readings are unaffected either way: the labels
are fields, not tags, so the device's history stays one series.

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
`pipeline.promotion.seed.location` at a `file:` resource to keep the list
outside the repository, or set `pipeline.promotion.seed.enabled=false` to skip
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
cannot silently revert it. Point `pipeline.classification.seed.location` at a `file:`
resource to keep the file outside the repository, or set
`pipeline.classification.seed.enabled=false` to skip it.

A seed entry is refused at startup if it has no `category`, if its fingerprint is
not the hash of its own structure, or if a mapping names a field that structure
does not have. Both are
ways a hand-edited file would otherwise produce a model that looks curated but
can never match a reading.

### What curation rejects

A curation must give a real `category` — not blank, and not the `uncategorised`
placeholder. A mapping must name a field the model's structure actually has, at
any depth, and its type must be one the mapping can read: `ANALOG` needs a
number, `BINARY` takes a number or a boolean, since rtl_433 encodes states as 0
and 1. Only a `BINARY` mapping may be inverted. A request breaking either rule is
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
