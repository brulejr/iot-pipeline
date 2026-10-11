# iot-pipeline

Kotlin / Spring Boot / Spring Integration pipeline that ingests 433 MHz sensor
readings (rtl_433 over MQTT), classifies them, and stores known-device data in
InfluxDB 3 for Grafana to read. `docs/DESIGN.md` is the authoritative design
doc: read it before changing pipeline structure.

Status: readings from promoted devices flow end to end — ingest, fingerprint and
dedupe, classification, the promotion gate, parsing, and InfluxDB storage. The
recommendation engine (stage 4) is a stub in `pipeline/StageStubFlows.kt`;
transformation and publication (stages 6-8) are not started.

@.claude/CONVENTIONS.md

## Layout

Packages mirror pipeline stages, one package per stage
(`ingest/`, `classify/`, ...). `pipeline/PipelineChannels.kt` holds the channel
names wiring stages together; ingestion adapters are transport-specific
subpackages (`ingest/mqtt/`) that all end by sending a `SensorEnvelope` to
`PipelineChannels.INGEST`.

Each stage is fronted by a port interface (`ClassificationPort`,
`ModelRegistryPort`, `PromotionPort`, `ReadingStorePort`), so implementations
can be swapped without touching the flow wiring. Where a port has a
database-backed and a database-free implementation, mutually exclusive
`@ConditionalOnProperty` conditions pick exactly one — the tests select the
database-free ones — rather than relying on `@ConditionalOnMissingBean` ordering
between application beans, which Spring only guarantees for auto-configuration.

`PipelineTopologyTest` asserts the channel wiring against the graph Spring
Integration actually builds; update its expected map when a stage is rewired.

## Commands

```
./gradlew build          # compile + test
./gradlew test           # tests only
set -a; . ./.env; set +a  # the app needs the Mongo credentials; Compose reads .env, it does not
./gradlew bootRun        # app on :5001
docker compose up -d     # InfluxDB :8181, MongoDB :27017, Grafana :3000, Explorer :8888
./scripts/bootstrap.sh   # first run: mints admin token, writes .env
```

## Notes

- Java 21 toolchain. Jackson is the new `tools.jackson` 3.x namespace, not
  `com.fasterxml.jackson` — match the existing imports.
- Server port is 5001, not the Spring Boot default of 8080, which collides with
  other local Docker containers.
- Paho must stay an explicit dependency; it is optional in
  `spring-integration-mqtt`.
- Startup fails if InfluxDB is unreachable, by design: the app creates its
  database with a retention period before anything is written. Set
  `pipeline.store.enabled=false` to run without it.
- Every `.kt` file under `src/` opens with the MIT license header: a `/* */`
  block comment (not KDoc, which would attach to the first declaration) above
  the `package` line, carrying the copyright line and
  `SPDX-License-Identifier: MIT`. Copy it from any existing file when adding
  one. Gradle build scripts are exempt.
