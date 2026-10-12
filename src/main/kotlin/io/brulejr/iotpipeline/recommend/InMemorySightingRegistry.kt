/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.recommend

import io.brulejr.iotpipeline.classify.DeviceKey
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory [SightingRegistryPort], for running without a database: the tests select it
 * with `pipeline.sighting-registry.type=memory`. Nothing expires, and a restart forgets
 * every sighting, so recommendations start from scratch.
 */
class InMemorySightingRegistry : SightingRegistryPort {

    private val buckets = ConcurrentHashMap<Pair<DeviceKey, Instant>, SightingBucket>()

    override fun record(sighting: Sighting) {
        val hour = sighting.receivedAt.truncatedTo(ChronoUnit.HOURS)
        buckets.merge(sighting.deviceKey to hour, firstBucket(sighting, hour)) { existing, _ ->
            existing.copy(
                count = existing.count + 1,
                signalSum = existing.signalSum + (sighting.signal ?: 0.0),
                signalCount = existing.signalCount + if (sighting.signal != null) 1 else 0,
                lastSeen = maxOf(existing.lastSeen, sighting.receivedAt),
            )
        }
    }

    override fun bucketsSince(since: Instant): List<SightingBucket> =
        buckets.values.filter { !it.hour.isBefore(since) }

    private fun firstBucket(sighting: Sighting, hour: Instant) = SightingBucket(
        deviceKey = sighting.deviceKey,
        hour = hour,
        modelName = sighting.modelName,
        modelFingerprint = sighting.modelFingerprint,
        count = 1,
        signalSum = sighting.signal ?: 0.0,
        signalCount = if (sighting.signal != null) 1 else 0,
        lastSeen = sighting.receivedAt,
    )
}
