# Production

The application and everything it depends on, on one Docker host: the
application image, InfluxDB 3, MongoDB and Grafana. Deployment is done by Ansible,
outside this repository; this directory is what it runs.

## What the deployment does

From a checkout of this repository:

```bash
./gradlew bootJar
docker compose -f docker/prod/compose.prod.yaml --env-file <env file> up -d --build
```

`bootJar` writes `build/libs/iot-pipeline.jar`, which
[`../Dockerfile`](../Dockerfile) packages; the image is tagged
`iot-pipeline:${APP_VERSION:-latest}`. Tests run with `./gradlew build` if the
deployment should refuse a failing commit.

## What it must provide

The env file, kept by Ansible and never committed:

| Variable | Required | |
| --- | --- | --- |
| `INGEST_MQTT_URL` | yes | The broker rtl_433 publishes to |
| `PUBLISH_MQTT_URL` | yes | The broker Home Assistant listens on |
| `INFLUXDB3_TOKEN` | yes | Must match the token in the admin token file |
| `MONGO_USERNAME`, `MONGO_PASSWORD`, `MONGO_DATABASE` | yes | Create the MongoDB root user on first start, and the application's connection |
| `GRAFANA_ADMIN_PASSWORD` | yes | |
| `INFLUXDB3_ADMIN_TOKEN_FILE` | no | Path to the admin token file, default `docker/influxdb3/secrets/admin-token.json` in the checkout |
| `INGEST_MQTT_CLIENT_ID`, `PUBLISH_MQTT_CLIENT_ID` | no | Default `iot-pipeline` and `iot-pipeline-publisher` |
| `INFLUXDB3_DATABASE` | no | Default `sensors` |
| `APP_PORT`, `GRAFANA_PORT`, `INFLUXDB3_PORT` | no | Host ports, default 5001, 3000 and 8181 (InfluxDB on loopback only) |
| `APP_VERSION` | no | Image tag, default `latest` |

`scripts/bootstrap.sh` shows how the token file and the secrets are generated,
and can generate them on the host. Broker credentials, if needed, go in as
`PIPELINE_INGEST_MQTT_USERNAME` / `PIPELINE_INGEST_MQTT_PASSWORD` and
`PIPELINE_PUBLISH_MQTT_USERNAME` / `PIPELINE_PUBLISH_MQTT_PASSWORD` in an
override file adding them to the `app` service's environment.

The `prod` profile reads an `application-prod.yml` if one is on the classpath;
none ships, so the environment is the whole configuration. Variables reach the
application under their full property names (`PIPELINE_*`), which outrank any
profile file.

## Behaviour worth knowing

- **Start-up order.** The application waits for InfluxDB and MongoDB to report
  healthy, and fails rather than starts if InfluxDB is unreachable, since it
  creates its database with a retention period before writing. `restart:
  unless-stopped` retries it.
- **Exposure.** The application (5001) and Grafana (3000) are published on every
  interface; InfluxDB on loopback only; MongoDB not at all. The application's REST
  API has no authentication, and demoting a device deletes it from Home
  Assistant, so keep 5001 on a trusted network.
- **First start.** A new MongoDB restores curated models and promoted devices
  from `model-seed.json` and `promotion-seed.json`, bundled in the jar. Export
  them from the running system before redeploying onto an empty store; see the
  main README.
- **Grafana comes provisioned.** The InfluxDB datasource and the **Sensors**
  dashboard load from `../grafana/provisioning/`, as in `dev`. Its admin password
  is set from `GRAFANA_ADMIN_PASSWORD` only when the `grafana-data` volume is first
  created.
- **Explorer is not included.** Add it from `../dev/compose.dev.yaml` if wanted.
- **Keep in step with `dev`.** The InfluxDB, MongoDB and Grafana definitions are
  repeated here rather than included, so production can publish fewer ports.
  Image versions should move together.
