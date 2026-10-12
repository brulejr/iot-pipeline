# IoT Pipeline: Design Notes

Status: scaffolded. Ingestion works end to end over MQTT, readings are fingerprinted
and deduplicated, classification identifies device models by structural fingerprint,
the promotion gate admits only approved devices, their readings are parsed and stored
in InfluxDB, and unpromoted devices are ranked for promotion; transformation and
publication (stages 6-8) are not started. Local InfluxDB 3 + Grafana run via Compose. An earlier
prototype (event-driven model) was the starting point for this redesign.

## Goal

Ingest sensor data (initially JSON from RTL-SDR via rtl_433 over MQTT), classify it, vet unknown devices, store known-device data with a retention window, then transform and publish it to downstream consumers such as Home Assistant.

## Tech Stack (decided / leaning)

- Language / framework: Kotlin, Spring Boot, Spring Integration (idiomatic Kotlin DSL, coroutines where they help)
- Ingestion transport: Spring Integration MQTT adapter (wraps Eclipse Paho). Prototype used a raw Hive-style MQTT client.
- Storage: InfluxDB 3 Core (open source, MIT/Apache 2.0, single node). Chosen for schema-flexible tag/field model that suits heterogeneous sensor data. MongoDB alongside it for the model registry and, later, recommendations and promoted devices; readings and metadata have little in common and a time-series store is the wrong shape for a curated catalogue.
- Dashboards: Grafana as an external consumer of InfluxDB. No app-specific Grafana integration. Dashboard-as-code provisioning is a possible later nicety.
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
5. **Storage.** Known-device data lands in InfluxDB with a retention policy so data ages out automatically. Storage is the neutral handoff boundary; everything before it is consumer-agnostic.
6. **Transformation.** Reshapes neutral stored data into the format a specific consumer needs (e.g. Home Assistant).
7. **Publication / delivery.** Publishes the transformed data. Circuit-breaker-style retry handling so a failed publish does not force re-running the transformation. Whether publication and delivery are one stage or two is undecided.
8. **Alerting (future).** Possible later stage for broadcasting particular high-priority event types. Out of immediate scope.

Observability is wanted at each stage.

## Open Questions

- Design of circuit breaker and retry mechanics for publishing
- Details of the transformation stage and its per-consumer mappings
- Publication and delivery: one stage or two
- Retention window length and any downsampling policy in InfluxDB

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

The registry lives in MongoDB behind `ModelRegistryPort`, chosen over a relational
store because a model is naturally a document: an arbitrary payload structure plus a
nested list of sensor mappings. It is separate from InfluxDB, which holds readings, and
is expected to host the recommendation and promotion collections when those land.
The structural fingerprint is the document id, so the store cannot hold two records for
one structure. An in-memory implementation remains for tests.

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

Settled: one point per value rather than per reading, so each sensor is its own series.
A model gaining or losing a mapping does not reshape what is already stored, and a query
for temperature need not know which models report it. Tags carry what is worth filtering
by — the device key, the operator's name/type/area, the model and its category, and the
sensor's class. Analog and binary values use separate fields because InfluxDB fixes a
field's type on first write.

The application creates its own database with the retention period, before anything is
written. InfluxDB creates a database implicitly on first write and an implicit one has
no retention, so leaving creation to the write would quietly keep readings for ever.
Creation runs during bean initialisation rather than from an application runner: Spring
Integration starts its endpoints after every singleton is initialised but before runners
execute, and from a runner the first reading won the race.

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

## Next Steps

- Cover the MongoDB registries with tests against a real database
- Wire storage (stage 5) to InfluxDB 3, including the retention policy
