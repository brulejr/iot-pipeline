/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.recommend

/**
 * Scores one device from its hourly buckets. Pure, so the arithmetic can be checked on
 * its own.
 */
class RecommendationScorer(private val datafill: RecommendationDatafill) {

    /**
     * @param buckets the device's buckets within the window; at least one.
     * @param modelCurated whether its model carries sensor mappings.
     */
    fun score(buckets: List<SightingBucket>, modelCurated: Boolean): Recommendation {
        require(buckets.isNotEmpty()) { "a device with no sightings cannot be scored" }
        val latest = buckets.maxBy { it.lastSeen }
        val hoursHeard = buckets.map { it.hour }.distinct().size
        val medianSignal = median(buckets.mapNotNull { it.meanSignal })

        val persistence = (hoursHeard.toDouble() / datafill.windowHours).coerceIn(0.0, 1.0)
        val proximity = medianSignal
            ?.let { (it - datafill.signalFloor) / (datafill.signalCeiling - datafill.signalFloor) }
            ?.coerceIn(0.0, 1.0)
            // Neither near nor far: no signal is no evidence either way.
            ?: NEUTRAL_PROXIMITY
        val score = datafill.proximityWeight * proximity + (1 - datafill.proximityWeight) * persistence

        return Recommendation(
            deviceKey = latest.deviceKey,
            modelName = latest.modelName,
            modelFingerprint = latest.modelFingerprint,
            modelCurated = modelCurated,
            sightings = buckets.sumOf { it.count },
            hoursHeard = hoursHeard,
            windowHours = datafill.windowHours,
            medianSignal = medianSignal,
            persistence = persistence,
            proximity = proximity,
            score = score,
            recommended = hoursHeard >= datafill.minHoursHeard &&
                // No signal is no evidence of distance, so the floor cannot apply.
                (medianSignal == null || proximity >= datafill.minProximity),
            firstSeen = buckets.minOf { it.hour },
            lastSeen = latest.lastSeen,
        )
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    private companion object {
        const val NEUTRAL_PROXIMITY = 0.5
    }
}
