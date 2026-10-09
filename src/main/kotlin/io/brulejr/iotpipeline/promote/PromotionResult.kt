/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote

/** Outcome of a promotion attempt. */
sealed interface PromotionResult {

    data class Promoted(val device: PromotedDevice) : PromotionResult

    /**
     * The request was refused and nothing was written. Carries every problem found, not
     * just the first.
     */
    data class Invalid(val problems: List<PromotionProblem>) : PromotionResult
}

/** One thing wrong with a promotion request. */
data class PromotionProblem(val field: String, val reason: String)
