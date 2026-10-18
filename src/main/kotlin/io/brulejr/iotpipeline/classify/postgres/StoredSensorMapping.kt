/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify.postgres

import io.brulejr.iotpipeline.classify.SensorMapping
import io.brulejr.iotpipeline.classify.SensorType

/**
 * Stored shape of a [SensorMapping], one element of a model's `sensors` JSON column.
 * Kept apart from the domain type so renaming a field there does not silently stop
 * stored curation from reading back.
 */
data class StoredSensorMapping(
    val name: String,
    val type: SensorType,
    val classname: String,
    val friendlyName: String? = null,
    // Defaulted so mappings stored without it still read back.
    val inverted: Boolean = false,
) {
    fun toMapping() = SensorMapping(name, type, classname, friendlyName, inverted)

    companion object {
        fun of(mapping: SensorMapping) = StoredSensorMapping(
            mapping.name,
            mapping.type,
            mapping.classname,
            mapping.friendlyName,
            mapping.inverted,
        )
    }
}
