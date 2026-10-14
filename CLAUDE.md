# iot-pipeline

Kotlin / Spring Boot / Spring Integration pipeline that ingests 433 MHz sensor
readings (rtl_433 over MQTT), classifies them, and stores known-device data in
InfluxDB 3 for Grafana to read. `docs/DESIGN.md` is the authoritative design
doc: read it before changing pipeline structure.

Status: stages 1-7 work — ingest, fingerprint and dedupe, classification, the
promotion gate, recommendation of unpromoted devices, parsing, InfluxDB storage,
shaping for Home Assistant, and publication to the consumer's MQTT broker.
Alerting (stage 8) is not started.

@.claude/CONVENTIONS.md

## Layout

One package per feature, each a pipeline stage (`ingest/mqtt/`, `fingerprint/`,
`dedupe/`, `classify/`, `promote/`, `recommend/`, `parse/`, `store/`,
`transform/`, `publish/`). Following the conventions, each feature has exactly one
`*Config` declaring all its beans and flows, a validated `*Datafill`, a
`*Service` holding its logic (flows and controllers only call it), a
`*Controller` under `api/`, a `*HealthIndicator` and a `*InfoContributor`.
Cross-cutting pieces live in `pipeline/`: `PipelineChannels.kt` names the
channels joining the stages, `PipelineConfig` declares them and the error log,
and `RegistryType` and `requireResolvedMongoUri` serve every registry.
Ingestion adapters are transport-specific subpackages (`ingest/mqtt/`) that
all end by sending a `SensorEnvelope` to `PipelineChannels.INGEST`.

Storage sits behind ports (`ModelRegistryPort`, `PromotionPort`,
`SightingRegistryPort`, `ReadingStorePort`), and so does
`ReadingTransformerPort`. The registries are storage only: validation lives in
the services. Each registry's Datafill picks MongoDB or memory through
`registry: mongo|memory` (`pipeline.classification.registry`,
`pipeline.promotion.registry`, `pipeline.recommendation.registry`), and the
config's `when` builds one, looking MongoDB up lazily so a memory-only context
never touches it; the tests select `memory`. `@ConditionalOnProperty` is kept
only to switch a feature on or off (`pipeline.ingest.mqtt.enabled`,
`pipeline.store.enabled`).

`PipelineTopologyTest` asserts the channel wiring against the graph Spring
Integration actually builds; update its expected map when a stage is rewired.

## Commands

```
./gradlew build          # compile + test
./gradlew test           # tests only
set -a; . ./.env; set +a  # the app needs the Mongo credentials; Compose reads .env, it does not
./gradlew bootRun        # app on :5001
docker compose -f docker/dev/compose.dev.yaml --env-file .env up -d   # InfluxDB :8181, MongoDB :27017, Grafana :3000, Explorer :8888
./scripts/bootstrap.sh   # first run: mints admin token, writes .env
```

## Notes

- Java 21 toolchain. Jackson is the new `tools.jackson` 3.x namespace, not
  `com.fasterxml.jackson` — match the existing imports.
- Server port is 5001, not the Spring Boot default of 8080, which collides with
  other local Docker containers.
- Paho must stay an explicit dependency; it is optional in
  `spring-integration-mqtt`.
- `spring.data.mongodb.auto-index-creation` is off: annotation-driven indexes are
  built when the Mongo template is created, which connects at startup even with
  every registry in memory. A registry that needs an index creates it itself.
  Tests point `spring.mongodb.uri` at `127.0.0.1:1`, never a real server.
- Publication goes to its own broker (`pipeline.publish.mqtt.*`,
  `PUBLISH_MQTT_URL`), not the ingest one, and is on by default. Spring tests set
  `pipeline.publish.enabled=false` so nothing reaches a broker; keep that in any
  new `@SpringBootTest`.
- Docker content lives under `docker/`, one directory per usage (`dev`, `local`,
  `prod`) with its own `compose.<usage>.yaml` and README; `docker/Dockerfile`
  packages `build/libs/iot-pipeline.jar` from `./gradlew bootJar` and is shared.
  The boot jar excludes `application-local.yml`, which holds development
  credentials. Compose files run from the repo root with `--env-file .env`.
- Startup fails if InfluxDB is unreachable, by design: the app creates its
  database with a retention period before anything is written. Set
  `pipeline.store.enabled=false` to run without it.
- Every `.kt` file under `src/` opens with the MIT license header: a `/* */`
  block comment (not KDoc, which would attach to the first declaration) above
  the `package` line, carrying the copyright line and
  `SPDX-License-Identifier: MIT`. Copy it from any existing file when adding
  one. Gradle build scripts are exempt.
