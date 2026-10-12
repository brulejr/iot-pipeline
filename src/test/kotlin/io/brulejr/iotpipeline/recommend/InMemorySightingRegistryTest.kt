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
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemorySightingRegistryTest {

    private val key = DeviceKey("rtl433", "DSC-Security/2320475")

    private fun sighting(at: String, signal: Double?) =
        Sighting(key, "DSC-Security", "fp", Instant.parse(at), signal)

    @Test
    fun `sightings within one hour share a bucket`() {
        val registry = InMemorySightingRegistry()
        registry.record(sighting("2026-10-12T10:05:00Z", 15.0))
        registry.record(sighting("2026-10-12T10:55:00Z", 17.0))

        val bucket = registry.bucketsSince(Instant.EPOCH).single()

        assertEquals(Instant.parse("2026-10-12T10:00:00Z"), bucket.hour)
        assertEquals(2, bucket.count)
        assertEquals(16.0, bucket.meanSignal)
        assertEquals(Instant.parse("2026-10-12T10:55:00Z"), bucket.lastSeen)
    }

    @Test
    fun `a new hour starts a new bucket`() {
        val registry = InMemorySightingRegistry()
        registry.record(sighting("2026-10-12T10:59:59Z", 15.0))
        registry.record(sighting("2026-10-12T11:00:00Z", 15.0))

        assertEquals(2, registry.bucketsSince(Instant.EPOCH).size)
    }

    @Test
    fun `a sighting without a signal still counts`() {
        val registry = InMemorySightingRegistry()
        registry.record(sighting("2026-10-12T10:00:00Z", null))

        val bucket = registry.bucketsSince(Instant.EPOCH).single()

        assertEquals(1, bucket.count)
        assertNull(bucket.meanSignal)
    }

    @Test
    fun `only buckets from the requested hour onwards are returned`() {
        val registry = InMemorySightingRegistry()
        registry.record(sighting("2026-10-12T08:30:00Z", 15.0))
        registry.record(sighting("2026-10-12T10:30:00Z", 15.0))

        val recent = registry.bucketsSince(Instant.parse("2026-10-12T10:00:00Z"))

        assertEquals(listOf(Instant.parse("2026-10-12T10:00:00Z")), recent.map { it.hour })
    }
}
