# Documentation

Start with the [project README](../README.md) for an overview and quick start.
[Design](DESIGN.md) explains how the pipeline is built and why; each guide below
links to the part of it that concerns that guide.

## Using the pipeline

| Guide | Covers | Design |
| --- | --- | --- |
| [Operator console](console.md) | The `/ui` pages, and identifying a device with the live monitor | [Console](DESIGN.md#operator-console-and-live-monitor) |
| [Models and curation](models.md) | How models are discovered, curating their sensor mappings, what curation rejects, seeding and backups | [Model identification](DESIGN.md#fingerprinting-dedupe-and-model-identification) |
| [Devices](devices.md) | Recommendations for unpromoted devices, promoting, relabelling and demoting, the promotion gate | [Promotion](DESIGN.md#promotion), [Recommendation](DESIGN.md#recommendation) |
| [Stored readings](readings.md) | The `readings` table, retention, and querying with Grafana or SQL | [Parsing](DESIGN.md#parsing), [Storage](DESIGN.md#storage) |
| [Home Assistant](home-assistant.md) | Discovery and state messages, units, and how publication retries | [Transformation](DESIGN.md#transformation), [Publication](DESIGN.md#publication) |

## Running it

| Guide | Covers | Design |
| --- | --- | --- |
| [Configuration](configuration.md) | Environment variables, local overrides, and the secrets in `.env` | [Ingestion](DESIGN.md#ingestion) |
| [Operations](operations.md) | Actuator, the REST API, per-stage logging, and troubleshooting | [Pipeline](DESIGN.md#pipeline) |
| [Docker](../docker/README.md) | Running the application in containers, locally or in production | [Technology](DESIGN.md#technology) |

## Working on it

| Guide | Covers |
| --- | --- |
| [Development](development.md) | Building, testing, and the source layout |
| [Design](DESIGN.md) | Principles, the stage-by-stage design and its reasoning, open questions |
