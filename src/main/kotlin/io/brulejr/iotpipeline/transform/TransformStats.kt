/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.transform

/**
 * What transformation has produced since startup.
 *
 * @property readings readings transformed.
 * @property messages outbound messages those readings became.
 * @property topics distinct topics those messages went to.
 */
data class TransformStats(
    val readings: Long,
    val messages: Long,
    val topics: Int,
)
