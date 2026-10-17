/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.transform.homeassistant

/**
 * The Home Assistant device class for a binary mapping on a particular device.
 *
 * A model's mapping says what a field means for every device of that model, so a contact
 * sensor's `closed` field can only be curated as `opening`: the same model is fitted to
 * doors and windows alike. Which one a device guards is known only when it is promoted,
 * from its type. Home Assistant's `door`, `garage_door` and `window` classes are
 * refinements of `opening` with the same ON-means-open sense, so a device whose type
 * names one of them takes that class and keeps the mapping's inversion unchanged.
 *
 * Any other class, or a type naming no refinement (`contact`, say), is left as curated.
 */
fun homeAssistantBinaryClassFor(classname: String, deviceType: String): String {
    if (classname != OPENING) return classname
    val refinement = deviceType.trim().lowercase().replace(Regex("[\\s_-]+"), "_")
    return if (refinement in OPENING_REFINEMENTS) refinement else classname
}

private const val OPENING = "opening"

private val OPENING_REFINEMENTS = setOf("door", "garage_door", "window")
