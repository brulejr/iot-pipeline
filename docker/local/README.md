# Local provisioning

The application in a container, together with everything it depends on: the
[`dev`](../dev/README.md) services plus the image built from
[`../Dockerfile`](../Dockerfile). Use it to run the whole system on this machine
the way production runs it.

```bash
# First run only
./scripts/bootstrap.sh

# From the repository root
./gradlew bootJar
docker compose -f docker/local/compose.local.yaml --env-file .env up -d --build
```

The application is at http://localhost:5001 (`APP_PORT` moves it); the other
services are where [`dev`](../dev/README.md) puts them.

## Settings

The container runs the `prod` profile and takes everything from the environment.
The jar deliberately carries no `application-local.yml`, so nothing from IDE
development leaks in. Add to `.env`:

| Variable | Required | |
| --- | --- | --- |
| `INGEST_MQTT_URL` | yes | The broker rtl_433 publishes to, e.g. `tcp://10.10.30.23:1883` |
| `PUBLISH_MQTT_URL` | yes | The broker Home Assistant listens on |
| `INGEST_MQTT_CLIENT_ID` | no | Default `iot-pipeline-local` |
| `PUBLISH_MQTT_CLIENT_ID` | no | Default `iot-pipeline-local-publisher` |
| `APP_PORT` | no | Host port for the application, default 5001 |

PostgreSQL is wired over the compose network, with the credentials
`scripts/bootstrap.sh` wrote. The brokers have no defaults on purpose:
a broker picked by mistake is how a test run reached a production Home
Assistant. The client ids differ from production's so the two never knock each
other off a shared broker.

The compose file passes settings under their full property names
(`PIPELINE_PUBLISH_MQTT_URL` and so on). Those outrank a profile file; the short
placeholders in `application.yml` (`MQTT_URL`, `PUBLISH_MQTT_URL`) do not.

## Cautions

- **Stop the copy running in the IDE first.** Two instances would store every
  reading twice and publish everything twice, and a demotion would reach only
  one of them.
- **This shares the development stack.** Same project name, so the same
  PostgreSQL database and the same data volumes. If you develop against a
  different PostgreSQL, its curation and approvals are not here, and the seed
  files restore them instead.
- **`PUBLISH_MQTT_URL` decides what Home Assistant sees.** Point it at a test
  broker unless you mean to publish to the real one.
