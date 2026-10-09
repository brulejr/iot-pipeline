/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify.mongo

import io.brulejr.iotpipeline.classify.ModelRecord
import io.brulejr.iotpipeline.classify.SensorMapping
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * Stored shape of a [ModelRecord]. Kept apart from the domain type so Spring Data
 * annotations stay out of the classification package, and so a change to how models are
 * stored does not ripple into the pipeline.
 *
 * The structural fingerprint is the document id: it *is* the model's identity, so the
 * store cannot hold two documents for one structure.
 */
@Document("models")
data class ModelDocument(
    @Id val fingerprint: String,
    val source: String,
    val name: String?,
    val structure: String,
    val discoveredAt: Instant,
    val sensors: List<SensorMappingDocument> = emptyList(),
) {
    fun toRecord() = ModelRecord(
        fingerprint = fingerprint,
        source = source,
        name = name,
        structure = structure,
        discoveredAt = discoveredAt,
        sensors = sensors.map { it.toMapping() },
    )

    companion object {
        fun of(model: ModelRecord) = ModelDocument(
            fingerprint = model.fingerprint,
            source = model.source,
            name = model.name,
            structure = model.structure,
            discoveredAt = model.discoveredAt,
            sensors = model.sensors.map { SensorMappingDocument.of(it) },
        )
    }
}

/** Stored shape of a [SensorMapping]. */
data class SensorMappingDocument(
    val field: String,
    val sensorType: String,
    val unit: String?,
) {
    fun toMapping() = SensorMapping(field, sensorType, unit)

    companion object {
        fun of(mapping: SensorMapping) = SensorMappingDocument(
            mapping.field,
            mapping.sensorType,
            mapping.unit,
        )
    }
}
