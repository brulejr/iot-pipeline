/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.store.postgres

import io.brulejr.iotpipeline.classify.DeviceKey
import io.brulejr.iotpipeline.parse.ParsedReading
import io.brulejr.iotpipeline.parse.SensorValue
import io.brulejr.iotpipeline.pipeline.getInstant
import io.brulejr.iotpipeline.pipeline.toTimestamptz
import io.brulejr.iotpipeline.store.ReadingStorePort
import io.brulejr.iotpipeline.store.StoreDatafill
import io.brulejr.iotpipeline.store.StoredValue
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations
import org.springframework.transaction.support.TransactionOperations
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Writes parsed readings to PostgreSQL's `readings` table.
 *
 * One row per value rather than one per reading, so each sensor is a series in its own
 * right: a model gaining or losing a mapping does not reshape what is already stored,
 * and a query for temperature need not know which models happen to report it. A
 * reading's rows are written in one batch and one transaction, so it is kept whole or
 * not at all.
 *
 * What identifies a series is kept apart from what a person calls the device and where
 * it is. The labels are stored with every row, recording what the device was called
 * when the reading was taken, so relabelling it does not rewrite its history.
 *
 * Rows older than [StoreDatafill.retention] are deleted as readings are written, at
 * most once an hour. PostgreSQL has no retention period of its own; while nothing is
 * stored, nothing is deleted, but then nothing accumulates either.
 */
class PostgresReadingStore(
    private val jdbc: NamedParameterJdbcOperations,
    private val transactions: TransactionOperations,
    private val datafill: StoreDatafill,
    private val clock: Clock,
) : ReadingStorePort {

    private val log = LoggerFactory.getLogger(PostgresReadingStore::class.java)
    private val lastPurge = AtomicReference(Instant.MIN)

    override fun store(reading: ParsedReading) {
        if (reading.values.isEmpty()) return
        val rows = reading.values.map { rowFor(reading, it) }.toTypedArray()
        transactions.executeWithoutResult { jdbc.batchUpdate(INSERT, rows) }
        purgeIfDue()
    }

    /** Asks the server for its version, which proves it answers. */
    override fun describe(): String {
        val version = jdbc.jdbcOperations.queryForObject("SHOW server_version", String::class.java)
        return "PostgreSQL $version, table readings"
    }

    /** Served by the (source, device, time) index, newest first. */
    override fun recent(deviceKey: DeviceKey, limit: Int): List<StoredValue> = jdbc.query(
        """
        SELECT time, sensor, field, value, state FROM readings
        WHERE source = :source AND device = :device
        ORDER BY time DESC
        LIMIT :limit
        """,
        MapSqlParameterSource()
            .addValue("source", deviceKey.source)
            .addValue("device", deviceKey.id)
            .addValue("limit", limit),
    ) { rs, _ ->
        StoredValue(
            time = checkNotNull(rs.getInstant("time")),
            sensor = rs.getString("sensor"),
            field = rs.getString("field"),
            value = rs.getDouble("value").takeUnless { rs.wasNull() },
            state = rs.getBoolean("state").takeUnless { rs.wasNull() },
        )
    }

    internal fun rowFor(reading: ParsedReading, value: SensorValue): MapSqlParameterSource =
        MapSqlParameterSource()
            .addValue("time", reading.receivedAt.toTimestamptz())
            .addValue("source", reading.device.deviceKey.source)
            .addValue("device", reading.device.deviceKey.id)
            .addValue("model", reading.model.name ?: "unnamed")
            .addValue("category", reading.model.category)
            .addValue("sensor", value.mapping.classname)
            .addValue("field", value.mapping.name)
            .addValue("value", (value as? SensorValue.Analog)?.value)
            .addValue("state", (value as? SensorValue.Binary)?.on)
            .addValue("deviceName", reading.device.name)
            .addValue("deviceType", reading.device.type)
            .addValue("deviceArea", reading.device.area)

    /** Deletes rows past the retention period, unless that was done within the hour. */
    internal fun purgeIfDue() {
        val now = clock.instant()
        val last = lastPurge.get()
        // Only the thread that wins the swap purges; the rest carry on.
        if (Duration.between(last, now) < PURGE_INTERVAL || !lastPurge.compareAndSet(last, now)) return
        val deleted = jdbc.update(
            "DELETE FROM readings WHERE time < :cutoff",
            MapSqlParameterSource("cutoff", now.minus(datafill.retention).toTimestamptz()),
        )
        if (deleted > 0) log.info("Deleted {} reading value(s) older than {}", deleted, datafill.retention)
    }

    private companion object {
        val PURGE_INTERVAL: Duration = Duration.ofHours(1)

        const val INSERT = """
            INSERT INTO readings (time, source, device, model, category, sensor, field, value, state,
                                  device_name, device_type, device_area)
            VALUES (:time, :source, :device, :model, :category, :sensor, :field, :value, :state,
                    :deviceName, :deviceType, :deviceArea)
        """
    }
}
