/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.store

import io.brulejr.iotpipeline.classify.DeviceKey
import io.brulejr.iotpipeline.classify.ModelRecord
import io.brulejr.iotpipeline.classify.SensorMapping
import io.brulejr.iotpipeline.classify.SensorType
import io.brulejr.iotpipeline.parse.ParsedReading
import io.brulejr.iotpipeline.parse.SensorValue
import io.brulejr.iotpipeline.promote.PromotedDevice
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StoreServiceTest {

    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val reading = ParsedReading(
        device = PromotedDevice(DeviceKey("rtl433", "Acurite-Tower/A/3064"), "Back garden", "thermometer", "garden", now),
        model = ModelRecord("fp", "rtl433", "Acurite-Tower", "{}", now, "weather"),
        receivedAt = now,
        values = listOf(
            SensorValue.Analog(SensorMapping("temperature_C", SensorType.ANALOG, "temperature"), 21.0),
            SensorValue.Analog(SensorMapping("humidity", SensorType.ANALOG, "humidity"), 48.0),
        ),
    )

    private fun failingStore() = object : ReadingStorePort {
        override fun store(reading: ParsedReading) = throw IllegalStateException("PostgreSQL unreachable")
        override fun describe() = throw IllegalStateException("PostgreSQL unreachable")
    }

    @Test
    fun `a stored reading counts its values`() {
        val service = StoreService(DiscardingReadingStore(), clock)

        service.store(reading)

        assertEquals(StoreStats(readings = 1, values = 2, failures = 0, lastStoredAt = now), service.stats())
    }

    @Test
    fun `a failed write is counted and rethrown`() {
        val service = StoreService(failingStore(), clock)

        assertFailsWith<IllegalStateException> { service.store(reading) }

        assertEquals(StoreStats(readings = 0, values = 0, failures = 1, lastStoredAt = null), service.stats())
    }

    @Test
    fun `an unreachable store is reported down`() {
        val health = StoreHealthIndicator(StoreService(failingStore(), clock)).health()

        assertEquals("DOWN", health?.status?.code)
    }
}
