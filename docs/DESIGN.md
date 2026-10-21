# Design

This is the authoritative record of how iot-pipeline is built and why. The other
guides say how to *use* each part; this one says why it works the way it does, and
is the place to read before changing the pipeline's structure. Each section links
to the guide for that part of the system.

Stages 1 to 7 are built, from ingestion to publication for Home Assistant, along
with an operator console and live monitor. Alerting (stage 8) is not started.
The design began as a rework of an earlier, event-driven prototype; where a
decision departs from it, the reason is given.

## Goal

Ingest sensor data (initially JSON from RTL-SDR via rtl_433 over MQTT), classify
it, vet unknown devices, store known-device data with a retention window, then
transform and publish it to downstream consumers such as Home Assistant.

## Principles

- **A dumb edge.** The edge host — an Orange Pi Zero running rtl_433 with an
  RTL-SDR dongle — only forwards decoded data to MQTT, with no routing or
  classification logic on it. Everything that needs deciding is decided here,
  where it can be changed, tested and observed.
- **A person decides what is kept.** The antenna hears any 433 MHz device in
  range (neighbours, cars, noise), so most of what arrives is not of interest.
  Nothing is stored until someone has said how to read a kind of device and that
  a particular one matters. The pipeline helps with both, and decides neither.
- **Storage is the neutral boundary.** Everything before storage is
  consumer-agnostic; everything after it shapes data for one consumer. A second
  consumer is a second transformer, not a change upstream.
- **Swappable behind ports.** Storage sits behind ports (`ModelRegistryPort`,
  `PromotionPort`, `SightingRegistryPort`, `ReadingStorePort`), and so does
  transformation (`ReadingTransformerPort`). Each registry has a PostgreSQL and an
  in-memory implementation; the tests use memory.
- **Observable at every stage.** Each stage logs under its own category, reports
  health and info through Actuator, and has an endpoint of its own; Micrometer
  observes every channel and handler. See [Operations](operations.md).

## Technology

- **Kotlin, Spring Boot, Spring Integration**, using the Kotlin DSL. Stages are
  integration flows joined by named channels, so the pipeline's shape is visible
  in one place and checked by a test against the graph Spring actually builds.
- **MQTT through Spring Integration's adapter**, which wraps Eclipse Paho. The
  prototype used a raw Hive-style client.
- **PostgreSQL** for the readings and every registry alike; see
  [Storage](#storage). It replaced InfluxDB 3 Core (readings), which did not
  deploy on the target platform, and MongoDB (registries), so one database serves
  both. Dedicated to this application for now, in a schema of its own so it can
  move to a shared instance later.
- **Grafana** as an external consumer of PostgreSQL, reading through a
  SELECT-only role, with dashboards provisioned as code. The application has no
  Grafana integration of its own.
- **Thymeleaf and htmx** for the operator console; see
  [Operator console and live monitor](#operator-console-and-live-monitor).
- **Docker Compose** for deployment; the database is owned solely by this
  application. See [Docker](../docker/README.md).

## Pipeline

| Stage | Package | Reads from | Writes to | Guide |
| --- | --- | --- | --- | --- |
| 1. Ingestion | `ingest/mqtt` | the broker | `ingestChannel` | [Configuration](configuration.md) |
| 1.5 Fingerprint and dedupe | `fingerprint`, `dedupe` | `ingestChannel` | `fingerprintedChannel`, then `dedupedChannel` | [Models](models.md) |
| 2. Classification | `classify` | `dedupedChannel` | `classifiedChannel` | [Models](models.md) |
| 3. Promotion gate | `promote` | `classifiedChannel` | `promotedChannel`, `promotionGapChannel`, `unknownDeviceChannel` | [Devices](devices.md) |
| 4. Recommendation | `recommend` | `unknownDeviceChannel` | sightings | [Devices](devices.md) |
| 5. Parsing and storage | `parse`, `store` | `knownDeviceChannel` (via `promotedChannel`) | `parsedChannel`, then `storedChannel` | [Stored readings](readings.md) |
| 6. Transformation | `transform` | `storedChannel`, `demotedChannel` | `outboundChannel` | [Home Assistant](home-assistant.md) |
| 7. Publication | `publish` | `outboundChannel` | the consumer's broker | [Home Assistant](home-assistant.md) |
| 8. Alerting | — | — | — | not started |

`pipeline/PipelineChannels.kt` names every channel; `PipelineTopologyTest` asserts
the wiring. Every channel has exactly one subscriber: they are direct channels,
which load-balance rather than broadcast. The live monitor is fed by a wire tap on
classification (`monitorChannel`), and so sees every classified reading, whatever
the gate decides.

Fingerprinting and dedupe were not in the original sketch, which numbered the
stages 1 to 8; they sit between ingestion and classification as 1.5 because
everything after depends on one transmission being counted once.

## Ingestion

Adapter-agnostic. MQTT first; other adapters could follow (for example a periodic
REST poll of a weather station). A single MQTT topic pattern carries all rtl_433
data. Each adapter lives in a transport-specific subpackage and ends by sending a
`SensorEnvelope` to `ingestChannel`, carrying the decoded payload and a coarse
`source` annotation identifying where it came from, so no later stage sees
transport details. Source is one input to identity: a device key is the source
plus the device's own id.

## Fingerprinting, dedupe and model identification

Following the earlier prototype, every reading is fingerprinted immediately after
ingestion, producing three hashes:

- **event** — the reading itself. What dedupe compares.
- **device** — the transmitter, from source plus `model`/`channel`/`id`. Deliberately
  excludes the receiving antenna, so one device heard by two receivers hashes alike.
- **model** — the payload's *structure*: field names sorted, values replaced by the
  name of their type. Readings from the same kind of device share it.

Radio and demodulation metadata (`time`, `rssi`, `snr`, `noise`, `freq`, `mod`) is
excluded from the event and model hashes by name, at any depth. It describes neither
the reading nor the kind of device. Excluding it from the *event* hash is what lets
dedupe recognise rtl_433's repeated decodes of one transmission, whose `rssi` and
`time` drift slightly; measured against a live stream it roughly doubled the
duplicates caught. This is safe only because the dedupe window is short (5 seconds
by default) — two genuine transmissions carrying identical values would collapse if
the window outlived the sensor's reporting interval.

**Dedupe** remembers each device's last event hash for the length of the window and
drops a repeat, so later stages count one transmission once.

**Model identification** is structural rather than name-based: the model hash is the
identity, and a model's self-reported name is recorded but not trusted. Two firmware
revisions reporting different fields are two models under one name. Models register
themselves on first sighting, so the catalogue builds from live traffic instead of
being written out in advance.

Recognising a model still takes a human: someone supplies its sensor mappings, which
say how to read values out of its payload, and its category. A model without
mappings is registered but not recognised, and its readings cannot be parsed. This
is the only curated input. It replaces the hand-written matching rules that
preceded it, which an earlier plan would have written as type-safe Kotlin rules in
a rules engine: identifying by structure made rules unnecessary, leaving only the
reading of values to curate.

Curation is validated when it is made, not when it is used: a mapping must name a
field the structure has and a type that field can be read as, and a seeded model's
fingerprint must be the hash of its own structure. Without that a typo would store
happily, report the model recognised, and yield nothing at parse time, far from the
cause. Because curation is the only hand-made data, it is also kept in a seed file
restored at startup, which fills gaps but never overwrites what the API set.

The registry lives in PostgreSQL behind `ModelRegistryPort`, in a `models` table keyed
by the structural fingerprint, so the store cannot hold two records for one structure.
A model is partly a document, an arbitrary payload structure plus a nested list of
sensor mappings; the mappings are a `jsonb` column, since they are always read and
replaced whole.

How to curate: [Models and curation](models.md).

## Promotion

Promotion is per device and manual, keyed by the device key rather than by the
device fingerprint. The two are the same identity derived from the same fields, but
the key is what the logs print, so it is what an operator can act on.

Curating a model and promoting a device are separate decisions. The first says how to
read a kind of device, the second says this particular one matters, and gives it the
name, type and area it has to its owner. A reading only passes the gate when both
have happened; a promoted device whose model has no sensor mappings is logged as a
gap rather than dropped silently, because that combination is someone's oversight
rather than noise.

The recommendation engine proposes candidates for promotion, but nothing is
promoted without a person. More automated promotion is a possible future direction.

Demotion is reported on `demotedChannel`, so the transformer can withdraw the device
from its consumer; see [Transformation](#transformation). Approvals, like curation,
are restored at startup from a seed file that only fills gaps.

How to promote: [Devices](devices.md).

## Recommendation

Settled after measuring rather than porting the prototype. Frequency is measured as
persistence — distinct hours heard in the last 24 — not as readings per hour. The
prototype's "30 sightings in an hour" would never recommend an event-driven sensor such
as a DSC door contact, which may report a handful of times an hour while being in range
all day, yet would recommend a car parked nearby for half an hour.

Proximity is read from SNR. The prototype mapped RSSI from -90 to -10 dBm, but rtl_433
reports RSSI relative to full scale, so every observed device fell between 0 and -11 and
all would have clipped to maximum proximity, ranking a neighbour's sensor level with
your own. Observed SNR ran from about 15-17 dB for nearby devices to 5-6 dB for distant
ones, which sets the default scale.

Score is 0.6 × proximity + 0.4 × persistence, proximity leading as the better sign a
device is yours rather than a neighbour's. A device is recommended once heard in three of
the last 24 hours and with proximity of at least 0.5, about 11 dB. Three is the smallest
meaningful threshold: buckets are clock hours, so two would be satisfied by readings a
minute apart either side of the hour. The proximity floor is what keeps a neighbour's
always-on sensor from being flagged, which persistence alone would do; it is skipped for
a device whose readings carry no signal. The window stays at a day so persistence still
separates an afternoon from all day, and so a slowly reporting sensor is not forgotten
between check-ins. Scores are computed on request from hourly sighting buckets rather
than stored, so promoting a device or curating its model shows at once. Buckets expire
after a week.

The calibration rests on few samples: a handful of decodes per device other than the
operator's own tower. Worth revisiting once more sightings have accumulated.

How to read the ranking: [Devices](devices.md#recommendations).

## Parsing

A promoted reading's payload is read through its model's sensor mappings, applying
each mapping's `inverted` flag, into one value per mapping. Every mapping is
expected to produce a value: curation refuses a mapping naming a field the
structure lacks or a type it cannot read, and the model's fingerprint *is* that
structure, so a reading that matched the model carries exactly those fields. A
value that cannot be read anyway is left out rather than guessed at. Such a
reading is incomplete; it is stored with the values it has unless
`pipeline.parse.drop-incomplete` is set.

Polarity is decided here, from curation, rather than at publication by field name
as the prototype did: only the person curating a model knows which way round a
field runs, and rtl_433 and Home Assistant disagree (`battery_ok: 1` is healthy,
HA's `battery` ON is low).

## Storage

PostgreSQL holds everything the application keeps, readings and registries, in
one database. InfluxDB 3 Core held the readings and MongoDB the registries until
InfluxDB would not deploy on the target platform; PostgreSQL replaced both, so there is
one database to run and back up rather than two. It runs on every architecture in
question, and Grafana reads it with its own built-in data source. At this scale, a few
dozen devices and a month of readings, a plain table needs no time-series extension.

One row per value rather than per reading, so each sensor is its own series. A model
gaining or losing a mapping does not reshape what is already stored, and a query for
temperature need not know which models report it. The columns identifying a series are
only what cannot be relabelled — the device key, the model and its category, the
sensor's class and its payload field. The operator's name, type and area are stored
alongside (`device_name`, `device_type`, `device_area`), recording the labels a reading
was taken under, so relabelling a device does not rewrite its history. An analog value
is in `value` and a binary one in `state`; every row has exactly one. The column names
follow the InfluxDB measurement this replaced, so the dashboards carried over.

The application owns its schema, `iot_pipeline`, and creates its tables with Flyway
migrations. It connects as its own role, never the superuser, and Grafana as a role that
can only read: the database is dedicated for now, but moving it to a shared instance
means creating the same two roles and the schema there. Migrations run while the first
feature using PostgreSQL is created, not through Spring Boot's auto-configuration, which
would connect even in a context with every registry in memory; they still run before
Spring Integration starts its endpoints, so the tables exist before the first reading.
For the same reason startup fails if PostgreSQL is unreachable while anything uses it.

PostgreSQL has no retention period of its own, so the application deletes readings older
than `pipeline.store.retention` (30 days by default) as it writes new ones, at most once
an hour, and sightings older than a week the same way. A change to the retention applies
to everything already stored, which InfluxDB's fixed-at-creation period did not allow.

How to query: [Stored readings](readings.md).

## Transformation

Built for Home Assistant, the first consumer, behind `ReadingTransformerPort` so a
second consumer is a second implementation rather than a change upstream. It reads from
the stored channel, so nothing is shaped for a consumer before it has been kept.

Readings become MQTT device discovery: one retained discovery message per device,
listing an entity per sensor mapping, and a state message per reading keyed by payload
field. Field rather than class, because two probes of one class would collide.
Discovery is resent only when it changes — after startup, or when curation alters a
device — rather than with every reading as the prototype did.

Demotion withdraws a device: the promotion service reports it on the `demoted` channel,
and the transformer answers with empty retained messages for the device's discovery and
(when retained) state topics, which deletes the device in Home Assistant and clears what
the broker keeps. The transformer forgets the device's discovery, so promoting it again
recreates it. Removal goes through publication like everything else, held messages
included.

Ids and topics are derived from the device key, never the operator's free-text name,
since Home Assistant ids allow only letters, digits, `_` and `-`. Binary values go out
as plain `ON`/`OFF`, the curated `inverted` flag having been applied during parsing.
A contact is curated as `opening`, since one model fits doors and windows alike, and
the promoted device's type refines it to `door`, `window` or `garage_door`.

Units come from rtl_433's field-name convention (`temperature_C`, `pressure_hPa`), not
from curation. That also fixes the prototype reporting `temperature_F` as Celsius. A
source that does not encode units in its field names would need them curated.

What Home Assistant receives: [Home Assistant](home-assistant.md).

## Publication

One stage, publishing to the consumer's own MQTT broker rather than the ingest one.
It takes the transformer's messages one at a time, so a failure costs one message
rather than re-running anything upstream.

A message that cannot be delivered is held in memory, and so is everything after it
until the broker is back, at most one per topic: a newer message for a topic replaces
the held one and joins the back of the line. Holding is bounded by the number of
devices however long the outage, and drops nothing a consumer needs, since it only
wants the latest state and every reading is in PostgreSQL. It does keep discovery, which
matters because the transformer sends a device's discovery only when it changes.

While anything is held the circuit is open: new messages are held rather than tried,
so none overtakes one already waiting, and the held messages are retried in arrival
order after a backoff that doubles with each failed retry, up to a ceiling. The first
complete flush closes it. A persistent outbox was considered and rejected: a restart
already re-sends discovery and the next reading refreshes state, so persisting would
add a write per message to keep only stale states.

Paho's own reconnection is off; the service decides when to try again, so health can
say plainly whether the broker is answering.

## Operator console and live monitor

A server-rendered console under `/ui`: Thymeleaf pages made interactive with htmx,
styled with Bootstrap. It manages what the API manages (curating and deleting
models, promoting, editing and demoting devices, the recommendations) and adds a
live monitor of readings. It holds no logic of its own: every page calls the same
feature services the REST controllers do, so a rule such as "a mapping must say
what it reads" is enforced once, in the service, for both. Each feature keeps its
page controller in a `web` package beside its `api` one; `console/` holds the
dashboard and what the pages share. Its browser libraries are WebJars, served
from the jar, because the target platform need not have internet access.

**Why server-rendered.** The pages are forms and tables over a few hundred rows,
and the one live view streams already-rendered HTML. A single-page application
would add a build, a client-side model of every type, and an API surface to keep
in step, for nothing the pages need.

**Live monitor.** Identifying a device means exercising it, opening a door or
pressing a button, and seeing which transmission arrives. The monitor taps
`classifiedChannel` with a wire tap, so it sees every reading whatever the
promotion gate decides, unpromoted devices included. For each device it remembers
the last payload, in memory, for an hour, and compares each reading with the one
before it from the same device. A field that differs is marked changed. Radio
metadata (the fields dedupe ignores) is never a change, since it differs on every
transmission, and a device not heard within the hour is marked first heard. A
stream can follow one device key, which need not have been heard yet, or a
search, promoted or unpromoted devices, and only changes. With changes only, the
steady reports of everything nobody is touching drop away and the exercised
device stands out.

The tap never slows or fails the pipeline: observing a reading only records it and
offers it to each open stream's bounded queue, and a full queue drops the event.
Each stream drains its queue on its own virtual thread, rendering the reading's
HTML fragment and sending it as a server-sent event, which htmx's SSE extension
prepends to the feed. A stream that cannot be written to has lost its browser,
and closes. The number of open streams is capped.

The console and the API have no authentication: they are meant for a trusted
network, and demoting a device deletes it from Home Assistant.

How to use it: [Operator console](console.md).

## Alerting

A possible later stage for broadcasting particular high-priority event types — a
door opening at night, a leak sensor tripping. Out of immediate scope, and not
designed yet.

## Open questions

- Retention window length, and whether to downsample older readings rather than
  delete them.
- Recommendation thresholds, calibrated on few samples.
- Moving the database to a shared PostgreSQL when a second application needs one.
