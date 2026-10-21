# Stored readings

A promoted device's readings are parsed through its model's mappings and written
to PostgreSQL, where Grafana and any other client can read them.

Why one row per value, and why PostgreSQL: [Design](DESIGN.md#storage).

## The `readings` table

Each reading is written one row per value:

```sql
SELECT time, device_name, device_area, category, model, sensor, value, state
FROM readings ORDER BY time DESC
```

| Identifies the series | |
| --- | --- |
| `source`, `device` | The device key, as the gate and classifier print it |
| `model`, `category` | What kind of device it is, from the model |
| `sensor` | The mapping's `classname` — `temperature`, `battery`, and so on |
| `field` | The payload field it was read from, e.g. `temperature_C` |

| Recorded with it | |
| --- | --- |
| `value` / `state` | The reading: `value` for an analog one, `state` for a binary one; never both |
| `device_name`, `device_type`, `device_area` | What the device was to you when the reading was taken, from its promotion |

Filter and group on `device`, and read the labels from the `device_*` columns:
renaming or moving a device does not rewrite its history, and its old rows keep
the labels they were taken under. One row per value rather than per reading
means each sensor is a series in its own right, so a model gaining or losing a
mapping does not reshape what is already stored. The column names follow the
InfluxDB measurement this table replaced.

## Schema and retention

Everything lives in the `iot_pipeline` schema (`pipeline.database.schema`),
which the application owns and migrates itself with Flyway; the migrations are
in `src/main/resources/db/migration`. PostgreSQL has no retention period of its
own, so the application deletes readings older than `pipeline.store.retention`
(30 days by default) as it writes new ones, at most once an hour. A change to the
setting applies to everything stored, from the next restart. Set
`pipeline.store.enabled=false` to run the pipeline without writing anything;
readings are still parsed and logged.

## Querying the readings

**Grafana** at http://localhost:3000 is provisioned against the database, with a
**Sensors** dashboard under *Dashboards* → *IoT Pipeline*: current values,
temperature and humidity over time, battery state, and when each device was last
heard, filtered by area and device. It is kept as code in
`docker/grafana/provisioning/dashboards/` and is read-only in Grafana; edit the
JSON there, or save a copy under a new name. *Explore* → *PostgreSQL* → *Code*
runs ad hoc SQL. Grafana connects as `grafana_reader`, which can only read, so
nothing typed there can change the data. It is treated as an external read-only
consumer — the application has no Grafana integration of its own.

From the command line:

```bash
docker compose exec postgres psql -U postgres -d iot_pipeline -c \
  "SELECT time, device_name, sensor, value, state FROM iot_pipeline.readings ORDER BY time DESC LIMIT 10"
```

Any PostgreSQL client works too: connect to localhost:5432 as
`POSTGRES_APP_USERNAME`, or as `grafana_reader` to be sure of changing nothing.
