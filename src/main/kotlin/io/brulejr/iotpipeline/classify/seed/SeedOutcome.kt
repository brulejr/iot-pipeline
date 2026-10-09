/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify.seed

/**
 * What a seeding run did.
 *
 * @property restored models the store did not have, inserted with their mappings.
 * @property curated models already present but uncurated, given their mappings.
 * @property leftAlone models already carrying mappings. Curation through the API is
 *   authoritative, so a seed never overwrites it.
 */
data class SeedOutcome(
    val restored: Int = 0,
    val curated: Int = 0,
    val leftAlone: Int = 0,
) {
    val total: Int get() = restored + curated + leftAlone
}
