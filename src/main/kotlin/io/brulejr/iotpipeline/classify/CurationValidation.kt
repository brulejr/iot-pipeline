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
    sensors: List<SensorMapping>,
    jsonMapper: JsonMapper,
): CurationResult.Invalid? {
    val known = structureFieldNames(model.structure, jsonMapper)
    val problems = buildList {
        sensors.forEach { sensor ->
            if (sensor.name !in known) {
                add(CurationProblem(sensor.name, "not a field in this model's structure"))
            }
            if (sensor.inverted && sensor.type != SensorType.BINARY) {
                add(CurationProblem(sensor.name, "inverted applies only to a BINARY mapping"))
            }
        }
    }
    return if (problems.isEmpty()) null else CurationResult.Invalid(problems)
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
