/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.parse

import io.brulejr.iotpipeline.classify.ModelRecord
import io.brulejr.iotpipeline.promote.PromotedDevice
import java.time.Instant

/**
 * A reading with its payload read out through its model's sensor mappings.
 *
 * Storage is the design's neutral handoff boundary, and this is the neutral form that
 * crosses it: no rtl_433 field names, no transport detail, nothing consumer-specific.
 */
data class ParsedReading(
    val device: PromotedDevice,
    val model: ModelRecord,
    val receivedAt: Instant,
    val values: List<SensorValue>,
)
