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
 * Default [ClassificationPort] used until real rule sets exist: every reading is
 * [Classification.Unknown], keyed by the rtl_433 `model`/`channel`/`id` fields when
 * present so the recommendation engine can count sightings per device.
 */
class NoRulesClassifier : ClassificationPort {

    override fun classify(envelope: SensorEnvelope): Classification =
        Classification.Unknown(envelope, deviceKeyOf(envelope), reason = "no classification rules configured")

    private fun deviceKeyOf(envelope: SensorEnvelope): DeviceKey? {
        val parts = listOf("model", "channel", "id").map { field ->
            envelope.payload.get(field)?.takeIf { it.isValueNode }?.asString()
        }
        if (parts.first() == null) return null
        return DeviceKey(envelope.source, parts.filterNotNull().joinToString("/"))
    }
}
