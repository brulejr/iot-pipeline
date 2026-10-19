# IoT Pipeline: Design Notes

Status: scaffolded. Ingestion works end to end over MQTT, readings are fingerprinted
and deduplicated, classification identifies device models by structural fingerprint,
the promotion gate admits only approved devices, their readings are parsed, stored in
PostgreSQL, shaped for Home Assistant and published to its broker, and unpromoted devices
are ranked for promotion; alerting (stage 8) is not started. An operator console
manages the data and streams readings live. Local PostgreSQL + Grafana run via Compose. An earlier
prototype (event-driven model) was the starting point for this redesign.

## Goal

Ingest sensor data (initially JSON from RTL-SDR via rtl_433 over MQTT), classify it, vet unknown devices, store known-device data with a retention window, then transform and publish it to downstream consumers such as Home Assistant.

## Tech Stack (decided / leaning)

- Language / framework: Kotlin, Spring Boot, Spring Integration (idiomatic Kotlin DSL, coroutines where they help)
- Ingestion transport: Spring Integration MQTT adapter (wraps Eclipse Paho). Prototype used a raw Hive-style MQTT client.
- Storage: PostgreSQL, for the readings and every registry alike; see Storage below. It replaced InfluxDB 3 Core (readings), which did not deploy on the target platform, and MongoDB (registries), so one database serves both. Dedicated to this application for now, in a schema of its own so it can move to a shared instance later.
- Dashboards: Grafana as an external consumer of PostgreSQL, reading through a SELECT-only role. No app-specific Grafana integration. Dashboards are provisioned as code.
- Deployment: Docker Compose; the database is owned solely by this application.
- Edge hardware: Orange Pi Zero running rtl_433 with an RTL-SDR dongle. Kept dumb and lightweight: it only forwards decoded data to MQTT, with no routing or classification logic on it.

## Pipeline Stages

0. **Fingerprint and dedupe.** Sits between ingestion and classification; see the
   section below. Not numbered in the original sketch, but everything after it depends
   on one transmission being counted once.
1. **Ingestion.** Adapter-agnostic. MQTT first; other adapters later (e.g. periodic REST poll of a weather station). A single MQTT topic carries all rtl_433 data. Each message gets a coarse source-level annotation (metadata/envelope field) identifying where it came from.
2. **Classification.** Determines what kind of device/data a message is and resolves the rule set used to decode that payload. Source is one input to the rules; rules can be cross-source or source-aligned. Rule sets are manually curated for now. Rules written as type-safe Kotlin code are preferred (candidate: in-rules-engine), behind a pluggable interface/port so implementations can be swapped (e.g. hand-rolled pattern matching).
3. **Branch: known vs unknown device.**
   - Known: continue to normal processing.
   - Unknown: go to the recommendation engine.
4. **Recommendation engine.** Evaluates unknown devices by frequency and proximity to decide whether they are worth tracking. The antenna hears any 433 MHz device in range (neighbors, noise), so most are not of interest. Promotion to "known" is a manual review/approve step for now; more AI-driven promotion is a possible future direction.
5. **Storage.** Known-device data lands in PostgreSQL with a retention period so data ages out automatically. Storage is the neutral handoff boundary; everything before it is consumer-agnostic.
6. **Transformation.** Reshapes neutral stored data into the format a specific consumer needs (e.g. Home Assistant).
7. **Publication / delivery.** Publishes the transformed data. Circuit-breaker-style retry handling so a failed publish does not force re-running the transformation. One stage; see Publication below.
8. **Alerting (future).** Possible later stage for broadcasting particular high-priority event types. Out of immediate scope.

Observability is wanted at each stage.

## Open Questions

- Retention window length, and whether to downsample older readings rather than delete them

## Fingerprinting, Dedupe and Model Identification

Settled, following the earlier prototype. Every reading is fingerprinted immediately
after ingestion, producing three hashes:

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
duplicates caught. This is safe only because the dedupe window is short — two genuine
transmissions carrying identical values would collapse if the window outlived the
sensor's reporting interval.

**Dedupe** remembers each device's last event hash for the length of the window and
drops a repeat, so later stages count one transmission once.

**Model identification** is structural rather than name-based: the model hash is the
identity, and a model's self-reported name is recorded but not trusted. Two firmware
revisions reporting different fields are two models under one name. Models register
themselves on first sighting, so the catalogue builds from live traffic instead of
being written out in advance.

Recognising a model still takes a human: someone supplies its sensor mappings, which
say how to read values out of its payload. A model without mappings is registered but
not recognised, and its readings cannot be parsed. This is the only curated input, and
it replaces the hand-written matching rules that preceded it.

Whether a *device* is known remains a separate question answered by promotion, also
manual. Until stage 3 exists, every reading goes to the recommendation engine carrying
its model.

The registry lives in PostgreSQL behind `ModelRegistryPort`, in a `models` table keyed
by the structural fingerprint, so the store cannot hold two records for one structure.
A model is partly a document, an arbitrary payload structure plus a nested list of
sensor mappings; the mappings are a `jsonb` column, since they are always read and
replaced whole. An in-memory implementation remains for tests.

## Promotion

Settled: promotion is per device and manual, keyed by the device key rather than by the
device fingerprint. The two are the same identity derived from the same fields, but the
key is what the logs print, so it is what an operator can act on.

Curating a model and promoting a device are separate decisions. The first says how to
read a kind of device, the second says this particular one matters. A reading only
passes the gate when both have happened; a promoted device whose model has no sensor
mappings is logged as a gap rather than dropped, because that combination is someone's
oversight rather than noise.

The recommendation engine will propose candidates for promotion, but nothing is
promoted without a person.

## Storage

Settled: PostgreSQL holds everything the application keeps, readings and registries, in
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

PostgreSQL has no retention period of its own, so the application deletes readings older
than `pipeline.store.retention` as it writes new ones, at most once an hour, and
sightings older than a week the same way. A change to the retention applies to
everything already stored, which InfluxDB's fixed-at-creation period did not allow.

## Recommendation

Settled, after measuring rather than porting the prototype. Frequency is measured as
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
operator's own tower. Worth revisiting once a few days of sightings have accumulated.

## Transformation

Settled for Home Assistant, the first consumer, behind `ReadingTransformerPort` so a
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
recreates it. Removal goes through publication like everything else, outbox included.

Ids and topics are derived from the device key, never the operator's free-text name,
since Home Assistant ids allow only letters, digits, `_` and `-`. Binary values go out
as plain `ON`/`OFF`: the prototype inverted `battery_ok` and `closed` by field name at
this point, which the curated `inverted` flag, applied during parsing, replaces.

Units come from rtl_433's field-name convention (`temperature_C`, `pressure_hPa`), not
from curation. That also fixes the prototype reporting `temperature_F` as Celsius. A
source that does not encode units in its field names would need them curated.

## Publication

Settled as one stage, publishing to the consumer's own MQTT broker rather than the
ingest one. It takes the transformer's messages one at a time, so a failure costs one
message rather than re-running anything upstream.

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

## Operator Console and Live Monitor

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

## Next Steps

- Move the database to a shared PostgreSQL when a second application needs one
