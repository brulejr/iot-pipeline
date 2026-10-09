/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify

import java.time.Instant

/**
 * A device model as the pipeline knows it, keyed by the fingerprint of its payload
 * structure rather than by its self-reported name. Two firmware revisions reporting
 * different fields are different models even under one name.
 *
 * @property sensors how to read values out of this model's payload. Curated by hand;
 *   until it is non-empty the model is registered but not recognised, so its readings
 *   cannot be parsed.
 */
data class ModelRecord(
    val fingerprint: String,
    val source: String,
    val name: String?,
    val structure: String,
    val discoveredAt: Instant,
    val sensors: List<SensorMapping> = emptyList(),
) {
    val recognised: Boolean get() = sensors.isNotEmpty()
}
