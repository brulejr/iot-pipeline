/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify.mongo

import io.brulejr.iotpipeline.classify.SensorMapping
import io.brulejr.iotpipeline.classify.SensorType

/** Stored shape of a [SensorMapping]. */
data class SensorMappingDocument(
    val name: String,
    val type: SensorType,
    val classname: String,
    val friendlyName: String?,
    // Defaulted so documents curated before this field existed still read back.
    val inverted: Boolean = false,
) {
    fun toMapping() = SensorMapping(name, type, classname, friendlyName, inverted)

    companion object {
        fun of(mapping: SensorMapping) = SensorMappingDocument(
            mapping.name,
            mapping.type,
            mapping.classname,
            mapping.friendlyName,
            mapping.inverted,
        )
    }
}
