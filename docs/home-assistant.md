# Home Assistant

After a reading is stored it is shaped for Home Assistant's MQTT discovery and
published to the broker Home Assistant listens on (`PUBLISH_MQTT_URL`), which is
separate from the broker rtl_433 publishes to.

Why messages are shaped and retried this way: [Design](DESIGN.md#transformation)
and [publication](DESIGN.md#publication).

## Messages

Two kinds of message, both visible on the `pipeline.transform` log:

| Message | Topic | Retained |
| --- | --- | --- |
| Discovery: the device and an entity per sensor mapping | `homeassistant/device/<id>/config` | always |
| State: one per reading, values keyed by payload field | `iot-pipeline/<id>/state` | `retain-state`, default yes |

`<id>` comes from the device key — `rtl433_acurite_tower_a_3064` — never from the
name you gave it, which is free text: Home Assistant ids allow only letters,
digits, `_` and `-`. The name appears as the device's display name instead, and
the area as its suggested area.

Discovery is sent only when it changes: once after startup, and again when a
curation alters what the device reports. Each analog mapping becomes a `sensor`
with `state_class: measurement`, so Home Assistant keeps long-term statistics
for it; each binary mapping becomes a `binary_sensor` reporting plain `ON` and
`OFF`, the mapping's [`inverted`](models.md#curating-a-model) flag having been
applied when the payload was parsed.

Units are read from rtl_433's field names, which carry them: `temperature_C` is
°C, `temperature_F` °F, `pressure_hPa` hPa, `wind_avg_km_h` km/h, `humidity` %.
A field whose name carries no unit is sent without one.

Prefixes and retention are under `pipeline.transform.home-assistant` in
`application.yml`. `GET /api/outbound-messages` shows the latest message for each
topic.

Demoting a device removes it from Home Assistant, and relabelling one updates it;
see [Devices](devices.md#promoting-a-device).

## Publication and retries

Publication is on by default; set `pipeline.publish.enabled=false` to shape
messages without sending them. When the broker cannot be reached, nothing is
lost that matters: the failed message is held, and so is everything after it,
one per topic — a newer state replaces the one waiting, since Home Assistant
only wants the latest and every reading is in PostgreSQL anyway. Held messages
are retried in arrival order after a backoff that doubles from
`pipeline.publish.retry.initial-backoff` up to `max-backoff`, and health reports
`publish` DOWN until they are delivered. `GET /api/publish/pending` shows what
is waiting; `POST /api/publish/flush` retries at once. Held messages are kept in
memory, so a restart drops them, but discovery is sent again after a restart and
the next reading brings fresh state.
