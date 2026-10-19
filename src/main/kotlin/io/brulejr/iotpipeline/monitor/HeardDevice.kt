/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.monitor

import io.brulejr.iotpipeline.classify.DeviceKey
import tools.jackson.databind.JsonNode
import java.time.Instant

/**
 * A device the pipeline has heard recently, promoted or not, with what it last sent.
 *
 * Kept in memory only, for as long as `pipeline.monitor.heard-for` after the device was
 * last heard. Unlike the recommendation's sightings it is not limited to unpromoted
 * devices, and it keeps the payload, so the last thing any device said can be shown.
 *
 * @property firstHeard when the device was first heard since it was last forgotten.
 * @property readings readings heard from it since [firstHeard].
 * @property signal the last reading's signal-to-noise ratio, if it carried one.
 */
data class HeardDevice(
    val deviceKey: DeviceKey,
    val modelName: String?,
    val modelFingerprint: String,
    val firstHeard: Instant,
    val lastHeard: Instant,
    val readings: Long,
    val signal: Double?,
    val payload: JsonNode,
)
