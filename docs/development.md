# Development

## Building and testing

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

The Spring tests keep every registry in memory and never reach a broker. The
`*/postgres/*Test` classes run against a throwaway Testcontainers PostgreSQL, and
are skipped when Docker is not available.

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
├── store/            #   PostgreSQL storage
├── transform/        # Stage 6 — shaping for Home Assistant
├── publish/          # Stage 7 — delivery to the consumer's broker
├── monitor/          #   live stream of classified readings, devices heard recently
├── console/          # The operator console's dashboard and shared web pieces
└── pipeline/         # Cross-cutting: channels, error log, registry choice, schema migration

src/main/resources/
├── db/migration/     # Flyway migrations
├── templates/ui/     # The console's Thymeleaf pages
└── static/ui/        # Its stylesheet and script
```

Packages mirror pipeline stages. `pipeline/PipelineChannels.kt` holds the
channel names that wire stages together; every ingestion adapter, whatever its
transport, ends by publishing a `SensorEnvelope` to `PipelineChannels.INGEST`,
so later stages never see transport details.

Every feature package has the same shape: one `*Config` declaring its beans
and flows, a validated `*Datafill` for its `pipeline.*` settings, a `*Service`
holding its logic, a `*Controller`, a health indicator and an info
contributor. A feature with console pages keeps their controller in a `web`
package beside `api`, e.g. `promote/web/DevicePageController`. Storage sits
behind ports such as `ModelRegistryPort`, so PostgreSQL and in-memory
implementations swap without touching the flow wiring.

## Design and conventions

[Design](DESIGN.md) records the reasoning behind each stage; read it before
changing the pipeline's structure. Project conventions, written for Claude Code
but applying to anyone, are in [`CLAUDE.md`](../CLAUDE.md) and
[`.claude/CONVENTIONS.md`](../.claude/CONVENTIONS.md).
