/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify.postgres

import io.brulejr.iotpipeline.classify.ModelRecord
import io.brulejr.iotpipeline.classify.SensorMapping
import io.brulejr.iotpipeline.classify.SensorType
import io.brulejr.iotpipeline.fingerprint.FingerprintDatafill
import io.brulejr.iotpipeline.fingerprint.FingerprintService
import io.brulejr.iotpipeline.fingerprint.FingerprintedReading
import io.brulejr.iotpipeline.ingest.SensorEnvelope
import io.brulejr.iotpipeline.pipeline.PostgresTestDatabase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers(disabledWithoutDocker = true)
class PostgresModelRegistryTest {

    private val jsonMapper = JsonMapper.builder().build()
    private val registry = PostgresModelRegistry(PostgresTestDatabase.jdbc, jsonMapper)
    private val fingerprintService = FingerprintService(FingerprintDatafill(), jsonMapper)

    private val contact = SensorMapping("closed", SensorType.BINARY, "opening", "Contact", inverted = true)
    private val tamper = SensorMapping("tamper", SensorType.BINARY, "tamper")

    @BeforeEach
    fun reset() = PostgresTestDatabase.reset()

    private fun reading(json: String): FingerprintedReading {
        val envelope = SensorEnvelope("rtl433", "rtl_433/pi/events", Instant.now(), jsonMapper.readTree(json))
        return FingerprintedReading(envelope, fingerprintService.fingerprint(envelope))
    }

    private fun model(fingerprint: String, discoveredAt: String = "2026-10-18T10:00:00Z") =
        ModelRecord(fingerprint, "rtl433", "DSC-Security", "{}", Instant.parse(discoveredAt))

    @Test
    fun `a new structure registers once, then is found`() {
        val dsc = reading("""{"model":"DSC-Security","id":2256987,"closed":1}""")

        val first = registry.registerIfAbsent(dsc)
        val second = registry.registerIfAbsent(dsc)

        assertEquals(first, second)
        assertEquals("DSC-Security", first.name)
        assertEquals(ModelRecord.UNCATEGORISED, first.category)
        assertTrue(first.sensors.isEmpty())
        assertEquals(1, registry.all().size)
    }

    @Test
    fun `curation is stored whole and reads back`() {
        registry.insertIfAbsent(model("fp"))

        val curated = registry.updateCuration("fp", "security", listOf(contact, tamper))

        assertEquals(listOf(contact, tamper), curated!!.sensors)
        assertEquals("security", registry.find("fp")!!.category)
        assertEquals(listOf(contact, tamper), registry.find("fp")!!.sensors)
    }

    @Test
    fun `curating an unknown model finds nothing`() {
        assertNull(registry.updateCuration("missing", "security", listOf(contact)))
    }

    @Test
    fun `inserting a known fingerprint leaves the stored model alone`() {
        assertTrue(registry.insertIfAbsent(model("fp").copy(category = "security", sensors = listOf(contact))))

        assertFalse(registry.insertIfAbsent(model("fp")))

        assertEquals(listOf(contact), registry.find("fp")!!.sensors)
    }

    @Test
    fun `models are listed oldest discovery first`() {
        registry.insertIfAbsent(model("newer", "2026-10-18T12:00:00Z"))
        registry.insertIfAbsent(model("older", "2026-10-18T09:00:00Z"))

        assertEquals(listOf("older", "newer"), registry.all().map { it.fingerprint })
    }

    @Test
    fun `a deleted model is forgotten, curation and all`() {
        registry.insertIfAbsent(model("fp").copy(category = "security", sensors = listOf(contact)))

        assertTrue(registry.delete("fp"))

        assertNull(registry.find("fp"))
        assertFalse(registry.delete("fp"))
    }
}
