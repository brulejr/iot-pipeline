# Operator console

`http://localhost:5001/ui` (and `/`, which redirects there) is a server-rendered
console, Thymeleaf with htmx, over the same services as the
[REST API](operations.md#rest-api). Everything it does can also be done with
`curl`; the console is the easier way.

Why it is server-rendered, and how the live monitor stays out of the pipeline's
way: [Design](DESIGN.md#operator-console-and-live-monitor).

| Page | What it does |
| --- | --- |
| Dashboard | Each stage's health and counts, the best promotion candidates, the devices heard last; reloads itself |
| Live monitor | Readings streamed as they are classified, filtered to one device key, a search, promoted or unpromoted devices, and optionally only changes |
| Devices | Promoted devices: promote any key, edit, demote; a page for every device key, promoted or not, with its last payload and stored values |
| Recommendations | Unpromoted devices ranked, each one click from being watched live or promoted |
| Models | The catalogue, and curation against the model's structure, with each field's last value and a preview of what the saved curation reads |

## Identifying a device

The antenna hears every transmitter in range, so the first job is usually working
out which transmissions are yours. To identify a device, open the live monitor,
choose **Unpromoted** and **Only changes and first sightings**, then exercise it:
open the door, press the button, warm the sensor. The reading that arrives is that
device's, marked *first heard* if it was silent until now, or with each field that
changed since its previous transmission highlighted and the old value struck
through. Radio metadata such as `rssi` and `snr` is never counted as a change.
**Watch** narrows the stream to that device, and **Promote** opens the promotion
dialog with its key filled in. A key can be watched before it is ever heard, so a
device known only by the label on its back can be confirmed the moment it first
transmits.

## How the live monitor works

The live monitor sees every classified reading through a wire tap, whatever the
promotion gate decides, and remembers the devices heard in the last hour in memory
(`pipeline.monitor.*`). Each browser stream has its own bounded queue, so a slow
or forgotten tab drops events rather than slowing the pipeline.

## Offline and security

The console's browser libraries (Bootstrap, Bootstrap Icons, htmx) are served from
WebJars in the jar, so it works without internet access. Like the API it has no
authentication; keep port 5001 on a trusted network.
