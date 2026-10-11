/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * Checks [sensors] against [model] and returns the result, writing nothing. Shared by
 * every [ModelRegistryPort] so the rule cannot differ between implementations.
 */
fun validateCuration(
    model: ModelRecord,
    category: String,
    sensors: List<SensorMapping>,
    jsonMapper: JsonMapper,
): CurationResult.Invalid? {
    val types = structureFieldTypes(model.structure, jsonMapper)
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
            } else if (type !in sensor.type.readableStructureTypes) {
                // Without this a mapping would be stored, report the model recognised,
                // and then yield nothing when the payload was read.
                add(
                    CurationProblem(
                        sensor.name,
                        "is a $type, which a ${sensor.type} mapping cannot read; " +
                            "expected ${sensor.type.readableStructureTypes.joinToString(" or ")}",
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

/**
 * The structure types each kind of mapping can read. rtl_433 encodes a state as 0 or 1
 * rather than a JSON boolean, so a BINARY mapping accepts both.
 */
private val SensorType.readableStructureTypes: Set<String>
    get() = when (this) {
        SensorType.ANALOG -> setOf("number")
        SensorType.BINARY -> setOf("number", "boolean")
    }

/**
 * Field names in a model's structure mapped to the type recorded for each, at any
 * depth. Depth matters because the structure mirrors the payload's own nesting, and a
 * nested reading is still a reading. A field holding an object maps to `object`, since
 * there is no single value to read from it.
 */
internal fun structureFieldTypes(structure: String, jsonMapper: JsonMapper): Map<String, String> {
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
