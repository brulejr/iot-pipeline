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
 * One reading from a device nobody has promoted, as the recommendation engine notes it.
 *
 * @property signal the reading's signal-to-noise ratio in dB, or null if the payload
 *   carried none. A proxy for proximity: a nearby transmitter arrives well above the
 *   noise, a distant one barely clears it.
 */
data class Sighting(
    val deviceKey: DeviceKey,
    val modelName: String?,
    val modelFingerprint: String,
    val receivedAt: Instant,
    val signal: Double?,
)
