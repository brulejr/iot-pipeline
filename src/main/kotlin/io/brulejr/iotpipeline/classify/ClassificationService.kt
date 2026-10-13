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
import java.util.concurrent.atomic.AtomicLong

/**
 * Identifies each reading's model, and curates models.
 *
 * Every reading leaves classification with a model attached, because an unseen payload
 * structure registers itself. What varies is whether that model has been curated: only
 * a model with sensor mappings is [Classification.Recognised] and can have its payload
 * parsed.
 *
 * Curation's rules live here rather than in the registries, so every way of storing
 * models applies the same ones and a registry is only storage.
 */
class ClassificationService(
    private val registry: ModelRegistryPort,
    private val jsonMapper: JsonMapper,
) {

    private val classified = AtomicLong()
    private val recognised = AtomicLong()

    fun classify(reading: FingerprintedReading): Classification {
        classified.incrementAndGet()
        val model = registry.registerIfAbsent(reading)
        val deviceKey = DeviceKeyExtractor.from(reading.envelope)
        if (!model.recognised) {
            return Classification.Unrecognised(reading, deviceKey, model, NOT_CURATED)
        }
        // A curated model whose payload names no device leaves nothing to promote or
        // count, so it cannot be treated as recognised.
        if (deviceKey == null) {
            return Classification.Unrecognised(reading, null, model, NO_DEVICE_IDENTITY)
        }
        recognised.incrementAndGet()
        return Classification.Recognised(reading, deviceKey, model)
    }

    /** Every model seen so far, oldest discovery first. */
    fun models(): List<ModelRecord> = registry.all()

    fun model(fingerprint: String): ModelRecord? = registry.find(fingerprint)

    /** Restores a model whole, as a seed file describes it; false if already known. */
    fun restore(model: ModelRecord): Boolean = registry.insertIfAbsent(model)

    /**
     * Records what a person decides about a model: its category and its sensor mappings.
     * Refused whole, writing nothing, if any part breaks [validate]'s rules.
     */
    fun curate(fingerprint: String, category: String, sensors: List<SensorMapping>): CurationResult {
        val model = registry.find(fingerprint) ?: return CurationResult.ModelNotFound
        validate(model, category, sensors)?.let { return it }
        val curated = registry.updateCuration(fingerprint, category, sensors)
            // Deleted between the read and the write.
            ?: return CurationResult.ModelNotFound
        return CurationResult.Curated(curated)
    }

    /**
     * Checks a curation against [model], writing nothing; null if it is acceptable.
     *
     * A category must say what the model is. Every mapping must name a field the model's
     * structure has, of a type it can read, and only a binary mapping may be inverted.
     * Each rule closes a way for a model to report itself recognised while yielding
     * nothing when its payload is read. Every problem is reported, not just the first.
     */
    fun validate(model: ModelRecord, category: String, sensors: List<SensorMapping>): CurationResult.Invalid? {
        val types = structureFieldTypes(model.structure)
        val problems = buildList {
            if (category.isBlank()) {
                add(CurationProblem("category", "must not be blank"))
            } else if (category == ModelRecord.UNCATEGORISED) {
                add(CurationProblem("category", "must say what the model is, not '${ModelRecord.UNCATEGORISED}'"))
            }
            sensors.forEach { sensor ->
                val type = types[sensor.name]
                if (type == null) {
                    add(CurationProblem(sensor.name, "not a field in this model's structure"))
                } else if (type !in readableStructureTypes(sensor.type)) {
                    add(
                        CurationProblem(
                            sensor.name,
                            "is a $type, which a ${sensor.type} mapping cannot read; " +
                                "expected ${readableStructureTypes(sensor.type).joinToString(" or ")}",
                        ),
                    )
                }
                if (sensor.inverted && sensor.type != SensorType.BINARY) {
                    add(CurationProblem(sensor.name, "inverted applies only to a BINARY mapping"))
                }
            }
        }
        return if (problems.isEmpty()) null else CurationResult.Invalid(problems)
    }

    fun stats(): ClassificationStats {
        val total = classified.get()
        val known = recognised.get()
        return ClassificationStats(classified = total, recognised = known, unrecognised = total - known)
    }

    /**
     * The structure types each kind of mapping can read. rtl_433 encodes a state as 0 or
     * 1 rather than a JSON boolean, so a BINARY mapping accepts both.
     */
    private fun readableStructureTypes(type: SensorType): Set<String> = when (type) {
        SensorType.ANALOG -> setOf("number")
        SensorType.BINARY -> setOf("number", "boolean")
    }

    /**
     * Field names in a model's structure mapped to the type recorded for each, at any
     * depth. Depth matters because the structure mirrors the payload's own nesting, and a
     * nested reading is still a reading. A field holding an object maps to `object`,
     * since there is no single value to read from it.
     */
    private fun structureFieldTypes(structure: String): Map<String, String> {
        val types = linkedMapOf<String, String>()
        fun walk(node: JsonNode) {
            when {
                node.isObject -> node.properties().forEach { (name, child) ->
                    types[name] = if (child.isObject) "object" else child.asString()
                    walk(child)
                }
                node.isArray -> node.forEach { walk(it) }
            }
        }
        walk(jsonMapper.readTree(structure))
        return types
    }

    companion object {
        const val NOT_CURATED = "model has no curated sensor mappings"
        const val NO_DEVICE_IDENTITY = "payload identifies no device"
    }
}
