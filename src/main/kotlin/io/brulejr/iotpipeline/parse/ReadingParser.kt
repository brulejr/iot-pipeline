/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.parse

import io.brulejr.iotpipeline.classify.SensorMapping
import io.brulejr.iotpipeline.classify.SensorType
import io.brulejr.iotpipeline.promote.PromotedReading
import tools.jackson.databind.JsonNode

/**
 * Reads a promoted reading's payload through its model's sensor mappings.
 *
 * Every mapping is expected to produce a value: curation refuses a mapping naming a
 * field the model's structure lacks, or one whose type it cannot read, and the model's
 * fingerprint *is* that structure, so a reading that matched the model carries exactly
 * those fields. A value that cannot be read anyway is left out rather than guessed at,
 * and the gap is visible in the parsed value count.
 */
class ReadingParser {

    fun parse(reading: PromotedReading): ParsedReading {
        val payload = reading.classification.reading.envelope.payload
        return ParsedReading(
            device = reading.device,
            model = reading.classification.model,
            receivedAt = reading.classification.reading.envelope.receivedAt,
            values = reading.classification.model.sensors.mapNotNull { valueOf(it, payload) },
        )
    }

    private fun valueOf(mapping: SensorMapping, payload: JsonNode): SensorValue? {
        val field = payload.get(mapping.name)?.takeIf { it.isValueNode && !it.isNull } ?: return null
        return when (mapping.type) {
            SensorType.ANALOG -> field.takeIf { it.isNumber }
                ?.let { SensorValue.Analog(mapping, it.asDouble()) }

            // rtl_433 encodes a state as 0 or 1 rather than a JSON boolean, so both are
            // accepted. Inversion is applied here so nothing downstream has to remember.
            SensorType.BINARY -> rawState(field)
                ?.let { SensorValue.Binary(mapping, if (mapping.inverted) !it else it) }
        }
    }

    private fun rawState(field: JsonNode): Boolean? = when {
        field.isBoolean -> field.asBoolean()
        field.isNumber -> field.asInt() != 0
        else -> null
    }
}
