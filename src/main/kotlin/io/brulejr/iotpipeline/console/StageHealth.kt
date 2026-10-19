/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.console

/**
 * One stage's health indicator, as the dashboard shows it.
 *
 * @property stage the feature, e.g. `store`.
 * @property status the health status code, e.g. `UP`.
 */
data class StageHealth(
    val stage: String,
    val status: String,
    val details: Map<String, Any?>,
)
