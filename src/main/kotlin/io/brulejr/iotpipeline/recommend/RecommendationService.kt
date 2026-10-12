/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.recommend

import io.brulejr.iotpipeline.classify.ModelRegistryPort
import io.brulejr.iotpipeline.promote.PromotionPort
import java.time.Clock
import java.time.temporal.ChronoUnit

/**
 * Ranks the devices nobody has promoted, best candidate first.
 *
 * Computed on request from the sightings rather than stored, so a recommendation cannot
 * go stale: promoting a device removes it from the list at once, and curating its model
 * shows up straight away.
 */
class RecommendationService(
    private val sightings: SightingRegistryPort,
    private val models: ModelRegistryPort,
    private val promotions: PromotionPort,
    private val scorer: RecommendationScorer,
    private val datafill: RecommendationDatafill,
    private val clock: Clock,
) {

    fun recommendations(): List<Recommendation> {
        // The window is whole hours, the current partial one included.
        val since = clock.instant()
            .truncatedTo(ChronoUnit.HOURS)
            .minus((datafill.windowHours - 1).toLong(), ChronoUnit.HOURS)
        return sightings.bucketsSince(since)
            .groupBy { it.deviceKey }
            .filterKeys { promotions.find(it) == null }
            .map { (_, buckets) ->
                val modelFingerprint = buckets.maxBy { it.lastSeen }.modelFingerprint
                scorer.score(buckets, models.find(modelFingerprint)?.recognised == true)
            }
            .sortedByDescending { it.score }
    }
}
