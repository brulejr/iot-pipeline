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

/** How a payload field is read: a measurement, or an on/off state. */
enum class SensorType { ANALOG, BINARY }

/**
 * Maps one field in a model's payload to a reading the rest of the pipeline understands.
 *
 * @property name the payload field this mapping reads, e.g. `temperature_C`.
 * @property type whether the field carries a measurement or an on/off state.
 * @property classname the semantic class of the reading, e.g. `temperature`, `humidity`,
 *   `battery`. Downstream publishing maps it to a consumer's own vocabulary.
 * @property friendlyName optional display name for a consumer that wants one.
 */
data class SensorMapping(
    val name: String,
    val type: SensorType,
    val classname: String,
    val friendlyName: String? = null,
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

    /** The model with this structural fingerprint, or null if it has not been seen. */
    fun find(fingerprint: String): ModelRecord?

    /**
     * Replaces a model's sensor mappings, which is how a human makes it recognised.
     * Returns the updated model, or null if no model has that fingerprint.
     */
    fun curate(fingerprint: String, sensors: List<SensorMapping>): ModelRecord?
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

    override fun find(fingerprint: String): ModelRecord? = byFingerprint[fingerprint]

    override fun curate(fingerprint: String, sensors: List<SensorMapping>): ModelRecord? =
        byFingerprint.computeIfPresent(fingerprint) { _, model -> model.copy(sensors = sensors) }
}
