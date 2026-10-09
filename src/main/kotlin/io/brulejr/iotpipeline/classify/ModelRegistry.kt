/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify

import io.brulejr.iotpipeline.fingerprint.FingerprintedReading
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

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

/**
 * Maps a field in a model's payload to a reading the rest of the pipeline understands.
 */
data class SensorMapping(
    val field: String,
    val sensorType: String,
    val unit: String? = null,
)

/**
 * Registry of every model structure the pipeline has seen.
 *
 * Models register themselves on first sighting, so the catalogue builds from live
 * traffic instead of being written out in advance. Recognising a model still takes a
 * human: someone has to supply its [ModelRecord.sensors].
 */
interface ModelRegistryPort {

    /** Returns the model for this reading's structure, registering it if it is new. */
    fun registerIfAbsent(reading: FingerprintedReading): ModelRecord

    /** Every model seen so far, newest discovery last. */
    fun all(): List<ModelRecord>
}

/**
 * In-memory [ModelRegistryPort]. The catalogue is rebuilt from traffic on restart, and
 * curated sensor mappings do not survive one, so nothing stays recognised.
 *
 * TODO back this with a document store so curation persists.
 */
class InMemoryModelRegistry : ModelRegistryPort {

    private val byFingerprint = ConcurrentHashMap<String, ModelRecord>()

    override fun registerIfAbsent(reading: FingerprintedReading): ModelRecord =
        byFingerprint.computeIfAbsent(reading.fingerprint.model) {
            ModelRecord(
                fingerprint = reading.fingerprint.model,
                source = reading.envelope.source,
                name = reading.envelope.payload.get("model")?.takeIf { it.isValueNode }?.asString(),
                structure = reading.fingerprint.modelStructure,
                discoveredAt = Instant.now(),
            )
        }

    override fun all(): List<ModelRecord> = byFingerprint.values.sortedBy { it.discoveredAt }
}
