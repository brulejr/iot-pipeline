/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.recommend

import io.brulejr.iotpipeline.classify.ClassificationService
import io.brulejr.iotpipeline.classify.DeviceKey
import io.brulejr.iotpipeline.classify.InMemoryModelRegistry
import io.brulejr.iotpipeline.classify.ModelRecord
import io.brulejr.iotpipeline.classify.SensorMapping
import io.brulejr.iotpipeline.classify.SensorType
import io.brulejr.iotpipeline.promote.InMemoryPromotionRegistry
import io.brulejr.iotpipeline.promote.PromotedDevice
import io.brulejr.iotpipeline.promote.PromotionService
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecommendationServiceTest {

    private val now = Instant.parse("2026-10-12T12:30:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val datafill = RecommendationDatafill()
    private val sightings = InMemorySightingRegistry()
    private val models = InMemoryModelRegistry()
    private val promotions = InMemoryPromotionRegistry()
    private val service = RecommendationService(
        sightings,
        ClassificationService(models, JsonMapper.builder().build()),
        PromotionService(promotions),
        RecommendationScorer(datafill),
        datafill,
        clock,
    )

    private val near = DeviceKey("rtl433", "Acurite-Tower/A/3064")
    private val far = DeviceKey("rtl433", "Acurite-Tower/B/870")

    private fun heard(key: DeviceKey, hoursAgo: List<Long>, signal: Double, fingerprint: String = "fp") =
        hoursAgo.forEach { h ->
            sightings.record(Sighting(key, "Acurite-Tower", fingerprint, now.minus(h, ChronoUnit.HOURS), signal))
        }

    @Test
    fun `devices are ranked best candidate first`() {
        heard(far, (0L..2L).toList(), 5.7)
        heard(near, (0L..23L).toList(), 15.9)

        assertEquals(listOf(near, far), service.recommendations().map { it.deviceKey })
    }

    @Test
    fun `a promoted device is not recommended`() {
        heard(near, (0L..23L).toList(), 15.9)
        promotions.save(PromotedDevice(near, "Back garden sensor", "thermometer", "garden", now))

        assertTrue(service.recommendations().isEmpty())
    }

    @Test
    fun `sightings older than the window are ignored`() {
        // 24 hours ago falls just outside a 24 hour window that includes this hour.
        heard(near, listOf(24L, 30L), 15.9)

        assertTrue(service.recommendations().isEmpty())
    }

    @Test
    fun `whether the model is curated is reported`() {
        val model = ModelRecord(
            fingerprint = "curated-fp", source = "rtl433", name = "Acurite-Tower",
            structure = """{"temperature_C":"number"}""", discoveredAt = now,
            category = "weather",
            sensors = listOf(SensorMapping("temperature_C", SensorType.ANALOG, "temperature")),
        )
        models.insertIfAbsent(model)
        heard(near, listOf(0L), 15.9, fingerprint = "curated-fp")
        heard(far, listOf(0L), 5.7, fingerprint = "unknown-fp")

        val byKey = service.recommendations().associateBy { it.deviceKey }

        assertTrue(byKey.getValue(near).modelCurated)
        assertTrue(!byKey.getValue(far).modelCurated)
    }
}
