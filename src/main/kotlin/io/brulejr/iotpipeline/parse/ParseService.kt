/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.parse

import io.brulejr.iotpipeline.classify.ClassificationService
import io.brulejr.iotpipeline.classify.ModelRecord
import io.brulejr.iotpipeline.classify.SensorMapping
import io.brulejr.iotpipeline.classify.SensorType
import io.brulejr.iotpipeline.promote.PromotedReading
import tools.jackson.databind.JsonNode
import java.util.concurrent.atomic.AtomicLong

/**
 * Stage 5, first half: reads a promoted reading's payload through its model's sensor mappings.
 *
 * Every mapping is expected to produce a value: curation refuses a mapping naming a
 * field the model's structure lacks, or one whose type it cannot read, and the model's
 * fingerprint *is* that structure, so a reading that matched the model carries exactly
 * those fields. A value that cannot be read anyway is left out rather than guessed at,
 * and the gap is visible in the parsed value count. Such a reading is incomplete, and
 * `pipeline.parse.drop-incomplete` decides whether it is stored anyway.
 */
class ParseService(
    private val datafill: ParseDatafill,
    private val classification: ClassificationService,
) {

    private val parsed = AtomicLong()
    private val incomplete = AtomicLong()
    private val dropped = AtomicLong()

    fun parse(reading: PromotedReading): ParsedReading {
        val envelope = reading.classification.reading.envelope
        val model = reading.classification.model
        return ParsedReading(
            device = reading.device,
            model = model,
            receivedAt = envelope.receivedAt,
            values = values(model, envelope.payload),
        ).also {
            parsed.incrementAndGet()
            if (!isComplete(it)) incomplete.incrementAndGet()
        }
    }

    /** True when every one of the model's mappings yielded a value. */
    fun isComplete(reading: ParsedReading): Boolean = reading.values.size == reading.model.sensors.size

    /** Whether [reading] goes on to storage, or is dropped as incomplete. */
    fun accept(reading: ParsedReading): Boolean =
        (!datafill.dropIncomplete || isComplete(reading)).also { if (!it) dropped.incrementAndGet() }

    /**
     * What [payload] would parse to under the model with this fingerprint, or null if
     * there is no such model. Counts nothing and stores nothing.
     */
    fun preview(fingerprint: String, payload: JsonNode): List<SensorValue>? =
        classification.model(fingerprint)?.let { values(it, payload) }

    fun stats() = ParseStats(parsed.get(), incomplete.get(), dropped.get())

    private fun values(model: ModelRecord, payload: JsonNode): List<SensorValue> =
        model.sensors.mapNotNull { valueOf(it, payload) }

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
