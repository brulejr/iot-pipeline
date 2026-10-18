/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.recommend.postgres

import io.brulejr.iotpipeline.classify.DeviceKey
import io.brulejr.iotpipeline.pipeline.PostgresTestDatabase
import io.brulejr.iotpipeline.recommend.Sighting
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Testcontainers(disabledWithoutDocker = true)
class PostgresSightingRegistryTest {

    private val key = DeviceKey("rtl433", "DSC-Security/2320475")
    private val now = Instant.parse("2026-10-18T12:00:00Z")
    private val registry = PostgresSightingRegistry(PostgresTestDatabase.jdbc, Clock.fixed(now, ZoneOffset.UTC))

    @BeforeEach
    fun reset() = PostgresTestDatabase.reset()

    private fun sighting(at: String, signal: Double?) =
        Sighting(key, "DSC-Security", "fp", Instant.parse(at), signal)

    @Test
    fun `sightings within one hour share a bucket`() {
        registry.record(sighting("2026-10-18T10:05:00Z", 15.0))
        registry.record(sighting("2026-10-18T10:55:00Z", 17.0))
        // Arriving late does not move last-seen backwards.
        registry.record(sighting("2026-10-18T10:30:00Z", null))

        val bucket = registry.bucketsSince(Instant.EPOCH).single()

        assertEquals(Instant.parse("2026-10-18T10:00:00Z"), bucket.hour)
        assertEquals(3, bucket.count)
        assertEquals(16.0, bucket.meanSignal)
        assertEquals(Instant.parse("2026-10-18T10:55:00Z"), bucket.lastSeen)
        assertEquals("DSC-Security", bucket.modelName)
    }

    @Test
    fun `a sighting without a signal still counts`() {
        registry.record(sighting("2026-10-18T10:00:00Z", null))

        val bucket = registry.bucketsSince(Instant.EPOCH).single()

        assertEquals(1, bucket.count)
        assertNull(bucket.meanSignal)
    }

    @Test
    fun `only buckets from the requested hour onwards are returned`() {
        registry.record(sighting("2026-10-18T08:30:00Z", 15.0))
        registry.record(sighting("2026-10-18T10:30:00Z", 15.0))

        val recent = registry.bucketsSince(Instant.parse("2026-10-18T10:00:00Z"))

        assertEquals(listOf(Instant.parse("2026-10-18T10:00:00Z")), recent.map { it.hour })
    }

    @Test
    fun `concurrent sightings lose no count`() {
        val pool = Executors.newFixedThreadPool(8)
        repeat(200) { pool.submit { registry.record(sighting("2026-10-18T10:15:00Z", 10.0)) } }
        pool.shutdown()
        pool.awaitTermination(30, TimeUnit.SECONDS)

        assertEquals(200, registry.bucketsSince(Instant.EPOCH).single().count)
    }

    @Test
    fun `buckets over a week old are deleted as sightings are recorded`() {
        // A week and an hour before the clock's noon.
        registry.record(sighting("2026-10-11T11:30:00Z", 15.0))
        registry.record(sighting("2026-10-18T11:30:00Z", 15.0))

        assertEquals(listOf(Instant.parse("2026-10-18T11:00:00Z")), registry.bucketsSince(Instant.EPOCH).map { it.hour })
    }
}
