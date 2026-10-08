# iot-pipeline

Kotlin / Spring Boot / Spring Integration pipeline that ingests 433 MHz sensor
readings (rtl_433 over MQTT), classifies them, and stores known-device data in
InfluxDB 3 for Grafana to read. `docs/DESIGN.md` is the authoritative design
doc: read it before changing pipeline structure.

Status: early scaffold. Stages 3-8 of the design are stubs
(`pipeline/StageStubFlows.kt`).

@.claude/CONVENTIONS.md

## Layout

Packages mirror pipeline stages, one package per stage
(`ingest/`, `classify/`, ...). `pipeline/PipelineChannels.kt` holds the channel
names wiring stages together; ingestion adapters are transport-specific
subpackages (`ingest/mqtt/`) that all end by sending a `SensorEnvelope` to
`PipelineChannels.INGEST`.

Each stage is fronted by a port interface (`ClassificationPort`) with a default
no-op implementation registered via `@ConditionalOnMissingBean`, so real
implementations can be swapped in without touching the flow wiring.

## Commands

```
./gradlew build          # compile + test
./gradlew test           # tests only
./gradlew bootRun        # app on :5001
docker compose up -d     # InfluxDB :8181, Grafana :3000
./scripts/bootstrap-influxdb.sh   # first run: mints admin token, writes .env
```

## Notes

- Java 21 toolchain. Jackson is the new `tools.jackson` 3.x namespace, not
  `com.fasterxml.jackson` — match the existing imports.
- Server port is 5001, not the Spring Boot default of 8080, which collides with
  other local Docker containers.
- Paho must stay an explicit dependency; it is optional in
  `spring-integration-mqtt`.
- Every `.kt` file under `src/` opens with the MIT license header: a `/* */`
  block comment (not KDoc, which would attach to the first declaration) above
  the `package` line, carrying the copyright line and
  `SPDX-License-Identifier: MIT`. Copy it from any existing file when adding
  one. Gradle build scripts are exempt.
