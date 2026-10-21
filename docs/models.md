# Models and curation

A *model* is a kind of device — every Acurite tower sensor running one firmware,
say. The pipeline discovers models on its own; a person then *curates* each one,
saying how to read values out of its payload. Curation is the only hand-made data
in the pipeline.

Why models are identified by structure: [Design](DESIGN.md#fingerprinting-dedupe-and-model-identification).

## Fingerprinting and model identification

Every reading is fingerprinted right after ingestion, producing three hashes:
the **event** (the reading itself), the **device** (the transmitter), and the
**model** (the payload's structure, with values replaced by the name of their
type). Radio metadata such as `rssi`, `snr` and `time` is excluded from the
event and model hashes, since it describes neither the reading nor the kind of
device that sent it.

**Dedupe** remembers each device's last event hash for `pipeline.dedupe.window`
and drops a repeat, so rtl_433's repeated decodes of one transmission are
counted once.

**Classification** identifies the model from its structural hash rather than
from the self-reported `model` field, so two firmware revisions reporting
different fields are two models under one name. Models register themselves on
first sighting — the catalogue builds from live traffic, with nothing written
out in advance.

Recognising a model takes a human: someone supplies its sensor mappings, saying
how to read values out of its payload. Until then the model is registered but
not recognised, and `pipeline.classify` says so. That curation is the only
hand-maintained input to the stage.

The registry is held in PostgreSQL behind `ModelRegistryPort`, so discovered
models and their curated mappings survive a restart. Set
`pipeline.classification.registry=memory` to run without a database, which is what
the tests do.

## Curating a model

The **Models** page of the [console](console.md) curates a model against its
structure. From the command line, list what has been discovered, including each
model's structure and whether it is recognised:

```bash
curl -s localhost:5001/api/models | jq
```

Then curate one, keyed by its structural fingerprint. A curation says what the
model *is* and how to read it, both at once:

```bash
curl -X PUT localhost:5001/api/models/<fingerprint>/curation \
  -H 'Content-Type: application/json' \
  -d '{"category":"weather",
       "sensors":[
        {"name":"temperature_C","type":"ANALOG","classname":"temperature","friendlyName":"Temperature"},
        {"name":"humidity","type":"ANALOG","classname":"humidity","friendlyName":"Humidity"},
        {"name":"battery_ok","type":"BINARY","classname":"battery","friendlyName":"Battery","inverted":true}
      ]}'
```

`category` is what kind of thing the model is — `weather`, `security`, and so on.
It is a free string rather than a closed set, because the band carries new device
types faster than an enum could be extended. A model registers itself from live
traffic, where nobody knows the answer, so it starts as `uncategorised` until
someone curates it.

Each sensor mapping carries:

| Field | Meaning |
| --- | --- |
| `name` | The payload field this mapping reads |
| `type` | `ANALOG` for a measurement, `BINARY` for an on/off state |
| `classname` | Semantic class downstream publishing maps to a consumer's vocabulary |
| `friendlyName` | Optional display name |
| `inverted` | The field's truth is the opposite of what `classname` means to a consumer |

`inverted` exists because rtl_433 and Home Assistant disagree on polarity:
`battery_ok: 1` means the battery is *healthy*, while HA's `battery` class
treats ON as *low*; `closed: 1` means a contact is *shut*, while `opening`
treats ON as *open*. Only the person curating the model knows which way round a
field runs, so it is recorded here rather than guessed at publish time. It
applies to `BINARY` mappings only.

A model's contact is curated as `opening`, since one model fits doors and
windows alike. The promoted device's `type` refines it for Home Assistant: a
device of type `door`, `window` or `garage door` publishes that device class
instead, with the same polarity. Any other type keeps `opening`.

The model is recognised from the next reading onwards, and `pipeline.classify`
says so. An empty `sensors` list undoes it. `DELETE /api/models/{fingerprint}`
forgets a model and its curation: one still being heard registers itself again,
uncurated, and one in the [seed file](#seeding-and-backing-up-curation) is
restored at the next startup.

## What curation rejects

A curation must give a real `category` — not blank, and not the `uncategorised`
placeholder. A mapping must name a field the model's structure actually has, at
any depth, and its type must be one the mapping can read: `ANALOG` needs a
number, `BINARY` takes a number or a boolean, since rtl_433 encodes states as 0
and 1. Every mapping needs a `classname` saying what it reads. Only a `BINARY`
mapping may be inverted. A request breaking any of these rules is
refused whole, leaving existing mappings untouched, and reports every problem
at once rather than one per attempt:

```json
{"message":"bogus: not a field in this model's structure; humidity: inverted applies only to a BINARY mapping",
 "problems":[{"field":"bogus","reason":"not a field in this model's structure"},
             {"field":"humidity","reason":"inverted applies only to a BINARY mapping"}]}
```

Without those checks a typo would store happily, report the model recognised,
and yield nothing at parse time — surfacing in a later stage, far from the
cause.

To check a curation against a real payload before relying on it,
`POST /api/models/{fingerprint}/parse` returns what that payload would parse to.

## Seeding and backing up curation

Sensor mappings are the only hand-made data in the pipeline; everything else is
derived from live traffic and rebuilds itself. Emptying the store would lose
them, so they are kept in `src/main/resources/model-seed.json`, which is read at
startup.

The file is the same shape as `GET /api/models`, so a backup is an export:

```bash
curl -s localhost:5001/api/models \
  | jq '[.[] | select(.recognised) | {fingerprint, source, name, structure, category, sensors}]' \
  > src/main/resources/model-seed.json
```

Seeding is idempotent and never destructive:

| In the store | What a seed does |
| --- | --- |
| Model absent | Restored whole, mappings included |
| Model present, uncurated | Given the file's mappings |
| Model present, already curated | Left alone |

Curation through the API therefore stays authoritative — a stale seed file
cannot silently revert it. Point `pipeline.classification.seed.location` at a `file:`
resource to keep the file outside the repository, or set
`pipeline.classification.seed.enabled=false` to skip it.

A seed entry is refused at startup if it has no `category`, if its fingerprint is
not the hash of its own structure, or if a mapping names a field that structure
does not have. All three are
ways a hand-edited file would otherwise produce a model that looks curated but
can never match a reading.
