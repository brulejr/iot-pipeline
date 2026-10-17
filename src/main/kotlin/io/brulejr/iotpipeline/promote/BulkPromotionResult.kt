/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote

/** Outcome of promoting several devices at once. */
sealed interface BulkPromotionResult {

    /**
     * Every device was stored, each list in the order given.
     *
     * @property promoted devices that had no promotion before.
     * @property updated devices that were already promoted, whose promotion was replaced.
     */
    data class Promoted(
        val promoted: List<PromotedDevice>,
        val updated: List<PromotedDevice>,
    ) : BulkPromotionResult

    /**
     * The request was refused and nothing was written. Each problem's field is prefixed
     * with the index of the entry it belongs to, e.g. `[2].name`.
     */
    data class Invalid(val problems: List<PromotionProblem>) : BulkPromotionResult
}
