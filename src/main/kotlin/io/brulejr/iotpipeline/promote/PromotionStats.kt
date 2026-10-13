/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote

/** How the gate has decided since startup. */
data class PromotionStats(
    val promoted: Long,
    val promotedButUnreadable: Long,
    val unpromoted: Long,
)
