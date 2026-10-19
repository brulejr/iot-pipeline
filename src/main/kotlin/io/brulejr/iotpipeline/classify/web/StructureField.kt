/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify.web

/**
 * One field of a model's structure, as the model page lists it.
 *
 * @property type the type recorded in the structure: `number`, `boolean`, `string`
 *   or `object`.
 * @property example what a device of this model last sent for it, if one was heard
 *   recently.
 * @property mappedAs the class a current mapping reads it as, if one does.
 */
data class StructureField(
    val name: String,
    val type: String,
    val example: String?,
    val mappedAs: String?,
) {
    /** Only numbers and booleans can be read by a mapping. */
    val mappable: Boolean get() = type == "number" || type == "boolean"
}
