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
import io.brulejr.iotpipeline.promote.PromotedDevice
import java.time.Instant

/**
 * One classified reading, as a live stream shows it.
 *
 * Built once per reading however many streams are open, and only while one is.
 *
 * @property sequence increases with every event, so a page can tell them apart.
 * @property deviceKey null when the payload names no device.
 * @property promotion the device's promotion, or null if it has none.
 * @property signal the reading's signal-to-noise ratio, from the field recommendation
 *   scores by, or null if the payload carried none.
 * @property firstHeard true when the device was not among those heard recently, which
 *   is how a device switched on for the first time stands out.
 * @property changed true when any non-radio field differs from the device's previous
 *   transmission: the reading of a sensor being exercised.
 */
data class MonitorEvent(
    val sequence: Long,
    val receivedAt: Instant,
    val origin: String,
    val deviceKey: DeviceKey?,
    val modelName: String?,
    val modelFingerprint: String,
    val modelCurated: Boolean,
    val promotion: PromotedDevice?,
    val signal: Double?,
    val fields: List<MonitorField>,
    val firstHeard: Boolean,
) {
    val changed: Boolean get() = fields.any { it.changed }
}
