# Devices: recommendation and promotion

Curating a [model](models.md) says how to read that *kind* of device. It does not
adopt any particular one: the antenna hears every transmitter in range — yours,
the neighbours', passing cars — so a reading only continues past the gate once
someone approves the device itself. The recommendation engine helps decide which
devices those are; promotion is the approval.

Why the ranking and the gate work as they do: [Design](DESIGN.md#promotion) and
[recommendation](DESIGN.md#recommendation).

## Recommendations

Every reading from a device nobody has promoted is counted, by hour, and the
devices are ranked for review. The **Recommendations** page of the
[console](console.md) shows the list; so does the API:

```bash
curl -s localhost:5001/api/recommendations | jq
```

| Field | |
| --- | --- |
| `persistence` | Share of the last 24 hours the device was heard in, 0..1 |
| `proximity` | Median SNR placed between 5 and 17 dB, 0..1; 0.5 if no signal |
| `score` | 0.6 × proximity + 0.4 × persistence; the list is ordered by it |
| `recommended` | Heard in at least 3 of the last 24 hours, with proximity of at least 0.5 (about 11 dB) |
| `modelCurated` | Whether its model has sensor mappings; promote an uncurated one and it reaches the gate only to be reported as a gap |

**Persistence, not rate.** A door sensor that checks in once an hour is around
all day; a car parked nearby for half an hour sends hundreds of readings and is
gone. Counting the hours a device is heard in tells those apart where a count of
readings would not. Hours are clock hours, so three of them means a device has
been around for more than an hour; two would be satisfied by readings a minute
apart either side of the hour.

**Near as well as persistent.** A neighbour's sensor is around all day too, so
time alone would flag it. A device is only recommended once its signal is
strong enough as well. A device whose readings carry no signal is judged on
hours alone.

**Proximity from SNR, not RSSI.** rtl_433 reports RSSI relative to full scale,
so strong signals all read close to zero. SNR spreads near and far apart and does
not depend on the radio's gain.

You do not have to wait for the flag: every device heard in the window is
listed straight away, ranked, and can be promoted at any time. `recommended`
only marks the ones worth looking at first. Nothing is promoted automatically.

Sightings are kept for a week, in the `sightings` table, and the application
deletes older ones as it records new ones. The thresholds are in `application.yml` under
`pipeline.recommendation`; they were calibrated on few samples and are worth
revisiting as more devices are seen.

## Promoting a device

Devices are addressed by the key printed on every `pipeline.classify` line —
the source and the id:

```bash
curl -X PUT localhost:5001/api/promoted-devices/rtl433/Acurite-Tower/A/3064 \
  -H 'Content-Type: application/json' \
  -d '{"name":"Back garden sensor","type":"thermometer","area":"garden"}'
```

`name`, `type` and `area` are what the device is to you, as opposed to the
model's `category`, which is what kind of device it is. All three are required.

```bash
curl -s localhost:5001/api/promoted-devices | jq        # what is approved
curl -X DELETE localhost:5001/api/promoted-devices/rtl433/Acurite-Tower/A/3064
```

Demoting a device also removes it from Home Assistant: an empty retained message
goes to its discovery topic, which Home Assistant treats as deleting the device,
and another to its state topic, so the broker stops keeping its last reading.
Both go through publication, so a broker outage holds them like any other
message. Promoting the device again recreates it with its next reading.

### Promoting several at once

Several devices can be promoted in one call by posting them to the collection.
Here each device's key is in the body, as `source` and `id`, so one call can span
sources:

```bash
curl -X POST localhost:5001/api/promoted-devices \
  -H 'Content-Type: application/json' \
  -d '[{"deviceKey":{"source":"rtl433","id":"DSC-Security/2256987"},
        "name":"Kitchen sliding door","type":"contact","area":"kitchen"},
       {"deviceKey":{"source":"rtl433","id":"Acurite-Tower/A/3064"},
        "name":"Back garden sensor","type":"thermometer","area":"garden"}]'
```

It is all or nothing: every entry is checked first, and if any is invalid, or
names a device another entry already names, nothing is written and the `400` lists
each problem by entry, e.g. `[1].name`. A device that is already promoted is not
a problem: it is relabelled, as promoting it alone would. The response says which
is which, as `{"promoted": [...], "updated": [...]}`: `promoted` for devices that
were new, `updated` for those whose earlier promotion was replaced. Entries have
the shape of `GET /api/promoted-devices` and `promotion-seed.json`, so either can
be sent back as is.

### Relabelling a device

To relabel a device, promote it again with the new `name`, `type` or `area`.
Its next reading sends Home Assistant updated discovery, and the change shows
up on the existing device — with one exception. Home Assistant uses
`suggested_area` only when it creates a device, so a changed area is ignored
for one it already has. Either set the area in Home Assistant, which keeps the
device and its history as they are, or demote and promote the device, so Home
Assistant deletes it and creates it again in the new area. That costs the
readings that arrive between the two calls, and may briefly break the device's
graphs in Home Assistant. Stored readings are unaffected either way: the labels
are fields, not tags, so the device's history stays one series.

## The promotion gate

The gate sends a reading one of three ways:

| Device | Model | Goes to |
| --- | --- | --- |
| Promoted | Curated | `knownDevice` — normal processing |
| Promoted | Uncurated | `promotionGaps` — logged at WARN, since nobody said how to read it |
| Not promoted | Either | `unknownDevice` — the recommendation engine |

## Seeding and backing up approvals

Approvals live in PostgreSQL alongside the model registry, and are restored at
startup from `src/main/resources/promotion-seed.json` the same way
[curated mappings](models.md#seeding-and-backing-up-curation) are. The file
matches `GET /api/promoted-devices`, so a backup is an export:

```bash
curl -s localhost:5001/api/promoted-devices \
  | jq 'map({deviceKey, name, type, area})' \
  > src/main/resources/promotion-seed.json
```

Seeding only fills gaps: a device the store has no approval for is restored, one
already approved is left alone. A label corrected through the API therefore
survives the next restart rather than being reverted by a stale file. Point
`pipeline.promotion.seed.location` at a `file:` resource to keep the list
outside the repository, or set `pipeline.promotion.seed.enabled=false` to skip
it. An entry with a blank label or device key fails startup, rather than leaving
a device quietly unapproved — which would look identical to one nobody has
reviewed.
