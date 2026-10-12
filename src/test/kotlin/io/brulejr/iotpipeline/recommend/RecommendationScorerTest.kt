/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.recommend

import io.brulejr.iotpipeline.classify.DeviceKey
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecommendationScorerTest {

    private val datafill = RecommendationDatafill(
        windowHours = 24, minHoursHeard = 6, signalFloor = 5.0, signalCeiling = 17.0, proximityWeight = 0.6,
    )
    private val scorer = RecommendationScorer(datafill)
    private val key = DeviceKey("rtl433", "Acurite-Tower/B/870")
    private val start = Instant.parse("2026-10-12T00:00:00Z")

    /** One bucket per hour for [hours] hours, each with one sighting at [signal]. */
    private fun heard(hours: Int, signal: Double?) = (0 until hours).map { h ->
        val hour = start.plus(h.toLong(), ChronoUnit.HOURS)
        SightingBucket(
            deviceKey = key, hour = hour, modelName = "Acurite-Tower", modelFingerprint = "fp",
            count = 1, signalSum = signal ?: 0.0, signalCount = if (signal != null) 1 else 0,
            lastSeen = hour.plusSeconds(30),
        )
    }

    @Test
    fun `persistence is the share of the window a device was heard in`() {
        assertEquals(0.5, scorer.score(heard(12, 11.0), true).persistence)
        assertEquals(1.0, scorer.score(heard(24, 11.0), true).persistence)
    }

    @Test
    fun `proximity places the signal between floor and ceiling`() {
        assertEquals(0.5, scorer.score(heard(6, 11.0), true).proximity)
    }

    @Test
    fun `signal beyond the floor or ceiling is clamped`() {
        assertEquals(0.0, scorer.score(heard(6, 2.0), true).proximity)
        assertEquals(1.0, scorer.score(heard(6, 30.0), true).proximity)
    }

    @Test
    fun `no signal is neither near nor far`() {
        val recommendation = scorer.score(heard(6, null), true)

        assertNull(recommendation.medianSignal)
        assertEquals(0.5, recommendation.proximity)
    }

    @Test
    fun `score weights proximity over persistence`() {
        // proximity 1.0, persistence 0.25: 0.6 * 1.0 + 0.4 * 0.25
        assertEquals(0.7, scorer.score(heard(6, 17.0), true).score, 1e-9)
    }

    @Test
    fun `a device is recommended once heard in enough hours, not before`() {
        assertFalse(scorer.score(heard(5, 16.0), true).recommended)
        assertTrue(scorer.score(heard(6, 16.0), true).recommended)
    }

    @Test
    fun `persistence, not chattiness, decides`() {
        // A burst of 500 readings within a single hour: a car parked nearby.
        val burst = heard(1, 16.0).map { it.copy(count = 500) }
        val recommendation = scorer.score(burst, true)

        assertEquals(500, recommendation.sightings)
        assertFalse(recommendation.recommended)
    }

    @Test
    fun `the median resists a single odd hour`() {
        val buckets = heard(5, 15.0) + heard(1, 0.0).map { it.copy(hour = start.minus(1, ChronoUnit.HOURS)) }

        assertEquals(15.0, scorer.score(buckets, true).medianSignal)
    }

    @Test
    fun `the curation flag is carried through`() {
        assertFalse(scorer.score(heard(6, 16.0), false).modelCurated)
    }

    @Test
    fun `observed devices rank as expected`() {
        // Signal medians from captured traffic: a nearby tower heard all day against
        // a neighbour's tower that only occasionally decodes.
        val mine = scorer.score(heard(24, 15.9), true)
        val neighbour = scorer.score(heard(3, 5.7), true)

        assertTrue(mine.score > neighbour.score, "${mine.score} vs ${neighbour.score}")
        assertTrue(mine.recommended)
        assertFalse(neighbour.recommended)
    }
}
