/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.recommend

import io.brulejr.iotpipeline.classify.Classification
import io.brulejr.iotpipeline.classify.ClassificationService
import io.brulejr.iotpipeline.promote.PromotionService
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Stage 4: notes every reading from an unpromoted device, and ranks the devices nobody
 * has promoted, best candidate first.
 *
 * The antenna hears every 433 MHz transmitter in range, so most of what arrives here is
 * a neighbour's or a passing car's. Nothing is promoted automatically; this only
 * suggests, and a person decides.
 *
 * Recommendations are computed on request from the sightings rather than stored, so a recommendation cannot
 * go stale: promoting a device removes it from the list at once, and curating its model
 * shows up straight away.
 */
class RecommendationService(
    private val sightings: SightingRegistryPort,
    private val classification: ClassificationService,
    private val promotions: PromotionService,
    private val scorer: RecommendationScorer,
    private val datafill: RecommendationDatafill,
    private val clock: Clock,
) {

    private val recorded = AtomicLong()
    private val unidentified = AtomicLong()

    /** Counts a reading from an unpromoted device towards its device's sightings. */
    fun record(classification: Classification) {
        if (!datafill.enabled) return
        // A reading with no device identity cannot be counted towards anything.
        val deviceKey = classification.deviceKey ?: run {
            unidentified.incrementAndGet()
            return
        }
        val envelope = classification.reading.envelope
        sightings.record(
            Sighting(
                deviceKey = deviceKey,
                modelName = classification.model.name,
                modelFingerprint = classification.model.fingerprint,
                receivedAt = envelope.receivedAt,
                signal = envelope.payload.get(datafill.signalField)?.takeIf { it.isNumber }?.asDouble(),
            ),
        )
        recorded.incrementAndGet()
    }

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
                scorer.score(buckets, classification.model(modelFingerprint)?.recognised == true)
            }
            .sortedByDescending { it.score }
    }

    fun stats() = RecommendationStats(recorded.get(), unidentified.get())
}
