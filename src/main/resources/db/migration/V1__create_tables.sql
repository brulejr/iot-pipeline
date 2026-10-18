-- The application's tables. Unqualified: Flyway runs this in the schema named by
-- pipeline.database.schema, which is also on every application connection's search path.

-- Every model structure seen on the air, keyed by its structural fingerprint, with the
-- sensor mappings curated against it. The mappings are always read and replaced whole,
-- so they are one JSON column rather than a table of their own.
CREATE TABLE models (
    fingerprint   text        PRIMARY KEY,
    source        text        NOT NULL,
    name          text,
    structure     text        NOT NULL,
    discovered_at timestamptz NOT NULL,
    category      text        NOT NULL,
    sensors       jsonb       NOT NULL DEFAULT '[]'
);

-- Devices someone has approved. Keyed by the device key's two parts, since an rtl_433
-- device id contains slashes of its own and cannot be split back out of one string.
CREATE TABLE promoted_devices (
    source      text        NOT NULL,
    device_id   text        NOT NULL,
    name        text        NOT NULL,
    type        text        NOT NULL,
    area        text        NOT NULL,
    promoted_at timestamptz NOT NULL,
    PRIMARY KEY (source, device_id)
);

-- Hourly sightings of unpromoted devices, for recommendations. Rows older than a week
-- are deleted by the application.
CREATE TABLE sightings (
    source            text             NOT NULL,
    device_id         text             NOT NULL,
    hour              timestamptz      NOT NULL,
    model_name        text,
    model_fingerprint text             NOT NULL,
    count             bigint           NOT NULL,
    signal_sum        double precision NOT NULL,
    signal_count      bigint           NOT NULL,
    last_seen         timestamptz      NOT NULL,
    PRIMARY KEY (source, device_id, hour)
);

CREATE INDEX sightings_hour ON sightings (hour);

-- One row per sensor value, the design's neutral handoff boundary. Column names follow
-- the InfluxDB measurement this replaces, so dashboard queries carried over. What
-- identifies a series (source, device, model, category, sensor, field) is kept apart
-- from the labels a person chose (device_name, device_type, device_area), which record
-- what the device was called when the reading was taken. An analog value is in `value`,
-- a binary one in `state`, and every row has exactly one of them. Rows older than
-- pipeline.store.retention are deleted by the application.
CREATE TABLE readings (
    time        timestamptz      NOT NULL,
    source      text             NOT NULL,
    device      text             NOT NULL,
    model       text             NOT NULL,
    category    text             NOT NULL,
    sensor      text             NOT NULL,
    field       text             NOT NULL,
    value       double precision,
    state       boolean,
    device_name text             NOT NULL,
    device_type text             NOT NULL,
    device_area text             NOT NULL,
    CHECK ((value IS NULL) <> (state IS NULL))
);

-- Rows arrive in time order, which a BRIN index summarises in a few pages: enough for
-- the time-range scans every dashboard makes, and for the retention delete.
CREATE INDEX readings_time ON readings USING brin (time);

-- The latest readings of one device, which "last heard" and the current-value panels ask for.
CREATE INDEX readings_device_time ON readings (source, device, time DESC);
