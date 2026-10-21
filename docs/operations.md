# Operations and observability

How to see what the running pipeline is doing: Actuator, the REST API, and the
logs that follow each reading through the stages. The stages and the channels
joining them are laid out in [Design](DESIGN.md#pipeline).

## Actuator

Actuator exposes `health`, `info`, `metrics`, and `integrationgraph` under
`http://localhost:5001/mgmt`. Micrometer observations are enabled for every
Spring Integration channel and handler, so each stage is individually
observable.

Every feature contributes to both `health` and `info`. Health reports whether
the feature's backing store answers, with counts since startup: the MQTT
connection is down once the broker is lost, and storage is down when PostgreSQL
cannot be reached. Info shows each feature's settings, minus credentials.

## REST API

Each feature also has an endpoint of its own. The API has no authentication;
keep port 5001 on a trusted network.

| Endpoint | What it gives |
| --- | --- |
| `GET /api/ingest/mqtt` | Broker connection, messages received and malformed |
| `POST /api/fingerprints` | The hashes a payload would get, without processing it |
| `GET /api/dedupe` | Readings passed and discarded as duplicates |
| `GET /api/models`, `PUT /api/models/{fingerprint}/curation`, `DELETE /api/models/{fingerprint}` | The model registry, curation, and forgetting a model — see [Models](models.md) |
| `GET`/`POST /api/promoted-devices`, `PUT`/`DELETE …/{source}/{deviceId}` | Device approvals; `POST` promotes several at once — see [Devices](devices.md) |
| `GET /api/recommendations` | Unpromoted devices, best candidate first |
| `POST /api/models/{fingerprint}/parse` | What a payload would parse to under a model's curation |
| `GET /api/store` | Readings and values written, and failed writes |
| `GET /api/outbound-messages` | The latest Home Assistant message for each topic |
| `GET /api/publish`, `GET /api/publish/pending`, `POST /api/publish/flush` | Publication counts, messages held for retry, and a retry now |
| `GET /api/monitor`, `GET /api/monitor/heard` | Live-monitor counts, and every device heard this past hour with its last payload |

## Logs

Logs follow a reading through every stage, including the ones that drop it.
Each stage logs under a `pipeline.*` category, set to DEBUG by default:

| Category | Level | What it shows |
| --- | --- | --- |
| `pipeline.ingest` | DEBUG | Every envelope as it leaves the MQTT adapter, with the raw payload |
| `pipeline.fingerprint` | DEBUG | The event, device and model hashes derived for the reading |
| `pipeline.dedupe` | DEBUG | Readings discarded as duplicates |
| `pipeline.classify` | DEBUG | The model each reading was matched to, and whether it can be parsed |
| `pipeline.promote` | DEBUG | Readings admitted by the gate; WARN for a promoted device whose model is uncurated |
| `pipeline.unknown` | DEBUG | Readings whose device is not promoted, counted by the recommendation engine |
| `pipeline.known` | INFO | Readings from a promoted device |
| `pipeline.parse` | INFO | Values read out of a payload through its model's mappings; WARN for an incomplete reading dropped by `pipeline.parse.drop-incomplete` |
| `pipeline.store` | DEBUG | Readings written to PostgreSQL |
| `pipeline.transform` | DEBUG | Messages shaped for Home Assistant, with their topics |
| `pipeline.publish` | DEBUG | Each message published; WARN when the broker fails, INFO when held messages are delivered |
| `pipeline.errors` | WARN | Failures routed to the ingest error channel |

Each reading produces one line per stage, and each line adds information rather
than repeating the last — the raw payload appears only on the `ingest` line:

```
pipeline.ingest      : received from rtl_433/<host>/events: {"model":"Acurite-Tower","id":3064,...}
pipeline.fingerprint : event=05996555a7aa device=baf1bfc9bc94 model=75ca26ed8cf9
pipeline.classify    : unrecognised Acurite-Tower/A/3064, model Acurite-Tower [75ca26ed8cf9]: model has no curated sensor mappings
pipeline.unknown     : awaiting promotion: Acurite-Tower/A/3064 from rtl_433/<host>/events
```

A reading dropped as a duplicate stops after `pipeline.dedupe`:

```
pipeline.dedupe      : duplicate of device=baf1bfc9bc94 from rtl_433/<host>/events, discarded
```

`pipeline.classify` is where to look for which models are flowing, and which
still need sensor mappings.

`pipeline` is still at DEBUG by default, from before readings were stored; now
that PostgreSQL keeps them, set `logging.level.pipeline=INFO` for quieter logs.
`pipeline.known` stays silent until a device is promoted — expected, not a
fault.

## Troubleshooting

If you see nothing at all, the reading is not reaching the adapter. Check the
broker and topic first, then confirm message counts are rising:

```bash
curl -s localhost:5001/mgmt/metrics/spring.integration.send
```

If startup fails naming a missing placeholder, the PostgreSQL credentials were not
exported; see [Getting started](../README.md#getting-started).
