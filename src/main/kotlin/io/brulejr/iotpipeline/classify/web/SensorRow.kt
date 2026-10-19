/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify.web

import io.brulejr.iotpipeline.classify.SensorMapping
import io.brulejr.iotpipeline.classify.SensorType

/**
 * One row of the curation form: a sensor mapping as typed, which may not be valid yet.
 *
 * @property index the row's position in the form's field names, `sensors[index].name`.
 *   Rows can be removed in the browser, so indexes may have gaps.
 */
data class SensorRow(
    val index: Int,
    val name: String = "",
    val type: String = SensorType.ANALOG.name,
    val classname: String = "",
    val friendlyName: String = "",
    val inverted: Boolean = false,
) {
    /** A row left without a field is an unused one, not an error. */
    val blank: Boolean get() = name.isBlank()

    fun toMapping() = SensorMapping(
        name = name.trim(),
        type = SensorType.valueOf(type),
        classname = classname.trim(),
        friendlyName = friendlyName.trim().ifEmpty { null },
        inverted = inverted,
    )

    companion object {
        private val PARAMETER = Regex("""sensors\[(\d+)]\.(\w+)""")

        fun of(index: Int, mapping: SensorMapping) = SensorRow(
            index = index,
            name = mapping.name,
            type = mapping.type.name,
            classname = mapping.classname,
            friendlyName = mapping.friendlyName.orEmpty(),
            inverted = mapping.inverted,
        )

        /**
         * The rows a submitted form carries, in form order, read from parameters named
         * `sensors[i].field`. Read by hand rather than bound, so a gap left by a removed
         * row is just skipped.
         */
        fun fromParameters(parameters: Map<String, Array<String>>): List<SensorRow> =
            parameters.entries
                .mapNotNull { (key, values) ->
                    PARAMETER.matchEntire(key)?.let { it.groupValues[1].toInt() to (it.groupValues[2] to values.first()) }
                }
                .groupBy({ it.first }, { it.second })
                .toSortedMap()
                .map { (index, fields) ->
                    val byName = fields.toMap()
                    SensorRow(
                        index = index,
                        name = byName["name"].orEmpty(),
                        type = byName["type"]?.takeIf { type -> SensorType.entries.any { it.name == type } }
                            ?: SensorType.ANALOG.name,
                        classname = byName["classname"].orEmpty(),
                        friendlyName = byName["friendlyName"].orEmpty(),
                        inverted = byName["inverted"] == "true",
                    )
                }
    }
}
