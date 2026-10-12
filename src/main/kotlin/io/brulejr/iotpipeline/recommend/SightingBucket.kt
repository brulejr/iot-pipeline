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

/**
 * Every sighting of one device within one hour, reduced to what scoring needs.
 *
 * Hourly rather than per reading because the question is how persistently a device is
 * around, not how talkative it is: a door sensor reporting once an hour and a weather
 * station reporting every 17 seconds both occupy every hour of the day.
 *
 * @property hour the start of the hour, in UTC.
 */
data class SightingBucket(
    val deviceKey: DeviceKey,
    val hour: Instant,
    val modelName: String?,
    val modelFingerprint: String,
    val count: Long,
    val signalSum: Double,
    val signalCount: Long,
    val lastSeen: Instant,
) {
    /** Mean signal across the hour's sightings that carried one, or null if none did. */
    val meanSignal: Double? get() = if (signalCount > 0) signalSum / signalCount else null
}
