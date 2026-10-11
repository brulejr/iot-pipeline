/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote.seed

/**
 * What a promotion seeding run did.
 *
 * @property restored devices the store had no approval for, promoted from the file.
 * @property leftAlone devices already approved. Promotion through the API is
 *   authoritative, so a seed never overwrites it: a label corrected in the running
 *   system is not reverted by a stale file.
 */
data class PromotionSeedOutcome(
    val restored: Int = 0,
    val leftAlone: Int = 0,
) {
    val total: Int get() = restored + leftAlone
}
