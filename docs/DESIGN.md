# IoT Pipeline: Design Notes

Status: scaffolded. Ingestion (stage 1) works end to end over MQTT; classification
(stage 2) exists as a port with a no-op default; stages 3-8 are stubs in
`pipeline/StageStubFlows.kt`. Local InfluxDB 3 + Grafana run via Compose. An earlier
prototype (event-driven model) was the starting point for this redesign.

## Goal

Ingest sensor data (initially JSON from RTL-SDR via rtl_433 over MQTT), classify it, vet unknown devices, store known-device data with a retention window, then transform and publish it to downstream consumers such as Home Assistant.

## Tech Stack (decided / leaning)

- Language / framework: Kotlin, Spring Boot, Spring Integration (idiomatic Kotlin DSL, coroutines where they help)
- Ingestion transport: Spring Integration MQTT adapter (wraps Eclipse Paho). Prototype used a raw Hive-style MQTT client.
- Storage: InfluxDB 3 Core (open source, MIT/Apache 2.0, single node). Chosen for schema-flexible tag/field model that suits heterogeneous sensor data.
- Dashboards: Grafana as an external consumer of InfluxDB. No app-specific Grafana integration. Dashboard-as-code provisioning is a possible later nicety.
- Deployment: Docker Compose; the database is owned solely by this application.
- Edge hardware: Orange Pi Zero running rtl_433 with an RTL-SDR dongle. Kept dumb and lightweight: it only forwards decoded data to MQTT, with no routing or classification logic on it.

## Pipeline Stages

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

- Concrete format/structure of classification rules
- Frequency and proximity thresholds in the recommendation engine
- Design of circuit breaker and retry mechanics for publishing
- Details of the transformation stage and its per-consumer mappings
- Publication and delivery: one stage or two
- Retention window length and any downsampling policy in InfluxDB

## Next Steps

- Implement real classification rules behind `ClassificationPort`, replacing `NoRulesClassifier`
- Build the known/unknown branch (stage 3) on top of classification output
- Wire storage (stage 5) to InfluxDB 3, including the retention policy
