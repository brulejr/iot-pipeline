/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.recommend.mongo

import io.brulejr.iotpipeline.classify.DeviceKey
import io.brulejr.iotpipeline.recommend.SightingBucket
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * Stored shape of a [SightingBucket].
 *
 * The id combines the device key and the hour, so recording a sighting is a single
 * atomic upsert. Buckets expire a week after their hour, through an index
 * [MongoSightingRegistry] creates: scoring only looks back a day, and without expiry
 * every passing car would be kept for ever.
 */
@Document("sightings")
data class SightingBucketDocument(
    @Id val id: String,
    val source: String,
    val deviceId: String,
    val hour: Instant,
    val modelName: String? = null,
    val modelFingerprint: String,
    val count: Long = 0,
    val signalSum: Double = 0.0,
    val signalCount: Long = 0,
    val lastSeen: Instant,
) {
    fun toBucket() = SightingBucket(
        deviceKey = DeviceKey(source, deviceId),
        hour = hour,
        modelName = modelName,
        modelFingerprint = modelFingerprint,
        count = count,
        signalSum = signalSum,
        signalCount = signalCount,
        lastSeen = lastSeen,
    )

    companion object {
        fun idOf(deviceKey: DeviceKey, hour: Instant) =
            "${deviceKey.source}/${deviceKey.id}#${hour.epochSecond}"
    }
}
