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
 * How strongly one unpromoted device is suggested for promotion.
 *
 * @property modelCurated whether the device's model has sensor mappings. Promoting a
 *   device whose model is uncurated admits it past the gate only to report a gap, so
 *   this says whether curation has to come first.
 * @property hoursHeard distinct hours of the window the device was heard in.
 * @property medianSignal median of the hourly mean signal, or null if no reading carried
 *   one.
 * @property persistence [hoursHeard] as a share of the window, 0..1.
 * @property proximity [medianSignal] placed between the configured floor and ceiling,
 *   0..1, or a neutral 0.5 when there is no signal to judge by.
 * @property score proximity and persistence combined, 0..1; the list is ordered by it.
 * @property recommended whether the device has been around long enough, and near enough,
 *   to suggest: heard in enough hours, and with a strong enough signal. A device with
 *   no signal reading is judged on hours alone.
 */
data class Recommendation(
    val deviceKey: DeviceKey,
    val modelName: String?,
    val modelFingerprint: String,
    val modelCurated: Boolean,
    val sightings: Long,
    val hoursHeard: Int,
    val windowHours: Int,
    val medianSignal: Double?,
    val persistence: Double,
    val proximity: Double,
    val score: Double,
    val recommended: Boolean,
    val firstSeen: Instant,
    val lastSeen: Instant,
)
