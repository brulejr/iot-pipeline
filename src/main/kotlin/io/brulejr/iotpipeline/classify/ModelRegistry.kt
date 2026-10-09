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
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
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
     *
     * Every mapping must name a field the model's structure actually has. A mapping for
     * a field that is not there would make the model report itself recognised while
     * yielding nothing at parse time, and the mistake would surface in a later stage far
     * from its cause.
     */
    fun curate(fingerprint: String, sensors: List<SensorMapping>): CurationResult
}

/** Outcome of a curation attempt. */
sealed interface CurationResult {

    data class Curated(val model: ModelRecord) : CurationResult

    /** No model carries the given fingerprint. */
    data object ModelNotFound : CurationResult

    /** Named fields the model's structure does not contain; nothing was written. */
    data class UnknownFields(val fields: Set<String>) : CurationResult
}

/**
 * Checks [sensors] against [model] and returns the result, writing nothing. Shared by
 * every [ModelRegistryPort] so the rule cannot differ between implementations.
 */
fun validateCuration(
    model: ModelRecord,
    sensors: List<SensorMapping>,
    jsonMapper: JsonMapper,
): CurationResult? {
    val known = structureFieldNames(model.structure, jsonMapper)
    val unknown = sensors.map { it.name }.filterNot { it in known }.toSet()
    return if (unknown.isEmpty()) null else CurationResult.UnknownFields(unknown)
}

/**
 * Field names in a model's structure, at any depth. Depth matters because the structure
 * mirrors the payload's own nesting, and a nested reading is still a reading.
 */
internal fun structureFieldNames(structure: String, jsonMapper: JsonMapper): Set<String> {
    val names = linkedSetOf<String>()
    fun walk(node: JsonNode) {
        when {
            node.isObject -> node.properties().forEach { (name, child) ->
                names += name
                walk(child)
            }
            node.isArray -> node.forEach { walk(it) }
        }
    }
    walk(jsonMapper.readTree(structure))
    return names
}

/**
 * In-memory [ModelRegistryPort]. The catalogue is rebuilt from traffic on restart, and
 * curated sensor mappings do not survive one, so nothing stays recognised.
 *
 * TODO back this with a document store so curation persists.
 */
class InMemoryModelRegistry(private val jsonMapper: JsonMapper) : ModelRegistryPort {

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

    override fun curate(fingerprint: String, sensors: List<SensorMapping>): CurationResult {
        val model = byFingerprint[fingerprint] ?: return CurationResult.ModelNotFound
        validateCuration(model, sensors, jsonMapper)?.let { return it }
        val curated = model.copy(sensors = sensors)
        byFingerprint[fingerprint] = curated
        return CurationResult.Curated(curated)
    }
}
