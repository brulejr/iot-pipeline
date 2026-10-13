/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote

/** What the gate decided for a reading, so the routing reads as a decision rather than a string. */
enum class PromotionDecision {
    /** Approved and its model can be parsed: on to normal processing. */
    PROMOTED,

    /**
     * Approved, but nobody has said how to read this model's payload, so the reading is
     * not ready for normal processing however approved it is.
     */
    PROMOTED_BUT_UNREADABLE,

    /** Nobody has approved the device: on to the recommendation engine. */
    UNPROMOTED,
}
