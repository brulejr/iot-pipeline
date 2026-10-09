/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote

import io.brulejr.iotpipeline.classify.DeviceKey
import java.time.Instant

/**
 * A device someone has approved for normal processing.
 *
 * The antenna hears every 433 MHz transmitter in range, most of them nobody's business,
 * so a reading only continues past the gate once a human has said this device matters.
 * Promotion is per device, not per model: recognising an Acurite-Tower does not adopt a
 * neighbour's.
 *
 * Keyed by [DeviceKey] rather than by the device fingerprint. They are the same identity
 * derived from the same fields, but the key is the one that appears in the logs, so it is
 * the one an operator can act on.
 *
 * @property name what to call this device, e.g. `Back garden sensor`.
 * @property type what it is being used for, e.g. `thermometer`. Distinct from the
 *   model's category, which says what kind of device it is.
 * @property area where it is, e.g. `garden`.
 */
data class PromotedDevice(
    val deviceKey: DeviceKey,
    val name: String,
    val type: String,
    val area: String,
    val promotedAt: Instant,
)
