/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.recommend.postgres

import io.brulejr.iotpipeline.classify.DeviceKey
import io.brulejr.iotpipeline.pipeline.getInstant
import io.brulejr.iotpipeline.pipeline.toTimestamptz
import io.brulejr.iotpipeline.recommend.Sighting
import io.brulejr.iotpipeline.recommend.SightingBucket
import io.brulejr.iotpipeline.recommend.SightingRegistryPort
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * PostgreSQL-backed [SightingRegistryPort], so a day of history survives a restart and
 * recommendations do not start from nothing each time the application does.
 *
 * Recording a sighting is one atomic upsert on the device and hour, so concurrent
 * readings of one device cannot lose a count.
 *
 * Buckets are deleted a week after their hour: scoring only looks back a day, and
 * without expiry every passing car would be kept for ever. PostgreSQL has no expiring
 * index, so recording deletes what has expired, at most once an hour. Nothing is
 * recorded, and so nothing deleted, while no unpromoted device is heard, but then
 * nothing accumulates either.
 */
class PostgresSightingRegistry(
    private val jdbc: JdbcClient,
    private val clock: Clock,
) : SightingRegistryPort {

    private val log = LoggerFactory.getLogger(PostgresSightingRegistry::class.java)
    private val lastPurge = AtomicReference(Instant.MIN)

    override fun record(sighting: Sighting) {
        jdbc
            .sql(
                """
                INSERT INTO sightings (source, device_id, hour, model_name, model_fingerprint,
                                       count, signal_sum, signal_count, last_seen)
                VALUES (:source, :deviceId, :hour, :modelName, :modelFingerprint,
                        1, :signalSum, :signalCount, :receivedAt)
                ON CONFLICT (source, device_id, hour) DO UPDATE
                SET count = sightings.count + 1,
                    signal_sum = sightings.signal_sum + EXCLUDED.signal_sum,
                    signal_count = sightings.signal_count + EXCLUDED.signal_count,
                    last_seen = GREATEST(sightings.last_seen, EXCLUDED.last_seen)
                """,
            )
            .param("source", sighting.deviceKey.source)
            .param("deviceId", sighting.deviceKey.id)
            .param("hour", sighting.receivedAt.truncatedTo(ChronoUnit.HOURS).toTimestamptz())
            .param("modelName", sighting.modelName)
            .param("modelFingerprint", sighting.modelFingerprint)
            .param("signalSum", sighting.signal ?: 0.0)
            .param("signalCount", if (sighting.signal != null) 1 else 0)
            .param("receivedAt", sighting.receivedAt.toTimestamptz())
            .update()
        purgeIfDue()
    }

    override fun bucketsSince(since: Instant): List<SightingBucket> = jdbc
        .sql("SELECT * FROM sightings WHERE hour >= :since")
        .param("since", since.toTimestamptz())
        .query { rs, _ -> rs.toBucket() }
        .list()

    /** Deletes buckets past [RETENTION], unless that was done within [PURGE_INTERVAL]. */
    internal fun purgeIfDue() {
        val now = clock.instant()
        val last = lastPurge.get()
        // Only the thread that wins the swap purges; the rest carry on.
        if (Duration.between(last, now) < PURGE_INTERVAL || !lastPurge.compareAndSet(last, now)) return
        val deleted = jdbc
            .sql("DELETE FROM sightings WHERE hour < :cutoff")
            .param("cutoff", now.minus(RETENTION).toTimestamptz())
            .update()
        if (deleted > 0) log.debug("Deleted {} expired sighting bucket(s)", deleted)
    }

    private fun ResultSet.toBucket() = SightingBucket(
        deviceKey = DeviceKey(getString("source"), getString("device_id")),
        hour = checkNotNull(getInstant("hour")),
        modelName = getString("model_name"),
        modelFingerprint = getString("model_fingerprint"),
        count = getLong("count"),
        signalSum = getDouble("signal_sum"),
        signalCount = getLong("signal_count"),
        lastSeen = checkNotNull(getInstant("last_seen")),
    )

    internal companion object {
        /** Well beyond the scoring window, so a longer window still has history. */
        val RETENTION: Duration = Duration.ofDays(7)

        val PURGE_INTERVAL: Duration = Duration.ofHours(1)
    }
}
