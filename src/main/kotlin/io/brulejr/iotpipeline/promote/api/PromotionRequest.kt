/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote.api

/**
 * Body of a promotion request: what an operator says about a device they are approving.
 *
 * The device itself is named in the path, by the key that appears in the pipeline's own
 * log lines.
 */
data class PromotionRequest(
    val name: String,
    val type: String,
    val area: String,
)
