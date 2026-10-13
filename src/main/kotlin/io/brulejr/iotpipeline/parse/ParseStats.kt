/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.parse

/**
 * What parsing has produced since startup.
 *
 * @property parsed readings read through their model's mappings.
 * @property incomplete of those, readings where some mapping yielded no value.
 * @property dropped of those, readings not stored because they were incomplete.
 */
data class ParseStats(
    val parsed: Long,
    val incomplete: Long,
    val dropped: Long,
)
