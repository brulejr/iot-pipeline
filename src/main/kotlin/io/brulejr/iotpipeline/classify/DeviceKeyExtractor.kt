/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify

import io.brulejr.iotpipeline.ingest.SensorEnvelope

/**
 * Derives a [DeviceKey] from a reading, so it carries the identity of the device that
 * sent it rather than of the receiver that heard it.
 *
 * rtl_433 identifies a transmitter by `model`/`channel`/`id`. Keying on those rather
 * than on the receiving antenna means one physical device heard by two receivers yields
 * the same key, so the recommendation engine counts it once.
 *
 * TODO generalise per source once a non-rtl_433 ingestion adapter exists.
 */
object DeviceKeyExtractor {
    private val KEY_FIELDS = listOf("model", "channel", "id")

    /** The device key, or null when the payload carries too little to identify one. */
    fun from(envelope: SensorEnvelope): DeviceKey? {
        val parts = KEY_FIELDS.map { field ->
            envelope.payload.get(field)?.takeIf { it.isValueNode }?.asString()
        }
        if (parts.first() == null) return null
        return DeviceKey(envelope.source, parts.filterNotNull().joinToString("/"))
    }
}
