/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify

/** Outcome of a curation attempt. */
sealed interface CurationResult {

    data class Curated(val model: ModelRecord) : CurationResult

    /** No model carries the given fingerprint. */
    data object ModelNotFound : CurationResult

    /**
     * The mappings were refused and nothing was written. Carries every problem found,
     * not just the first, so one request does not have to be fixed a rule at a time.
     */
    data class Invalid(val problems: List<CurationProblem>) : CurationResult
}
