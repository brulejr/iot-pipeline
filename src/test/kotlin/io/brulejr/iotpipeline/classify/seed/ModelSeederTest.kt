/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify.seed

import io.brulejr.iotpipeline.classify.InMemoryModelRegistry
import io.brulejr.iotpipeline.classify.ModelRecord
import io.brulejr.iotpipeline.classify.SensorMapping
import io.brulejr.iotpipeline.classify.SensorType
import io.brulejr.iotpipeline.fingerprint.sha256Hex
import org.junit.jupiter.api.Test
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModelSeederTest {

    // The Kotlin module is what makes an omitted field fall back to its default, as it
    // does in the application, where Spring Boot registers it. Without it a seed file
    // leaving out `inverted` fails to deserialise.
    private val jsonMapper = JsonMapper.builder().addModule(kotlinModule()).build()

    private val structure = """{"humidity":"number","id":"number","model":"string","temperature_C":"number"}"""
    private val fingerprint = sha256Hex(structure)

    private fun seedJson(
        fp: String = fingerprint,
        sensors: String = """[{"name":"temperature_C","type":"ANALOG","classname":"temperature"}]""",
    ) = """[{"fingerprint":"$fp","source":"rtl433","name":"Acurite-Tower",
            "structure":${jsonMapper.writeValueAsString(structure)},"sensors":$sensors}]"""

    /** Serves one in-memory document, so the test needs no file on disk. */
    private fun loaderOf(json: String?) = object : ResourceLoader {
        override fun getResource(location: String): Resource = json
            ?.let { ByteArrayResource(it.toByteArray()) }
            // A classpath entry that genuinely is not there, so exists() is false.
            ?: ClassPathResource("no-such-model-seed.json")

        override fun getClassLoader(): ClassLoader = javaClass.classLoader
    }

    private fun seeder(
        registry: InMemoryModelRegistry,
        json: String?,
        datafill: ModelSeedDatafill = ModelSeedDatafill(),
    ) = ModelSeeder(registry, datafill, jsonMapper, loaderOf(json))

    private fun record(sensors: List<SensorMapping>) = ModelRecord(
        fingerprint = fingerprint,
        source = "rtl433",
        name = "Acurite-Tower",
        structure = structure,
        discoveredAt = Instant.now(),
        sensors = sensors,
    )

    @Test
    fun `a model the store does not have is restored with its mappings`() {
        val registry = InMemoryModelRegistry(jsonMapper)

        val outcome = seeder(registry, seedJson()).seed()

        assertEquals(SeedOutcome(restored = 1), outcome)
        val restored = registry.find(fingerprint)!!
        assertEquals("Acurite-Tower", restored.name)
        assertEquals(listOf("temperature_C"), restored.sensors.map { it.name })
        assertTrue(restored.recognised)
    }

    @Test
    fun `a model already present but uncurated is given its mappings`() {
        val registry = InMemoryModelRegistry(jsonMapper)
        registry.insertIfAbsent(record(emptyList()))

        val outcome = seeder(registry, seedJson()).seed()

        assertEquals(SeedOutcome(curated = 1), outcome)
        assertEquals(listOf("temperature_C"), registry.find(fingerprint)!!.sensors.map { it.name })
    }

    @Test
    fun `curation already in the store is never overwritten`() {
        val registry = InMemoryModelRegistry(jsonMapper)
        registry.insertIfAbsent(record(listOf(SensorMapping("humidity", SensorType.ANALOG, "humidity"))))

        val outcome = seeder(registry, seedJson()).seed()

        assertEquals(SeedOutcome(leftAlone = 1), outcome)
        // The store's own mapping survives, not the file's.
        assertEquals(listOf("humidity"), registry.find(fingerprint)!!.sensors.map { it.name })
    }

    @Test
    fun `seeding twice changes nothing the second time`() {
        val registry = InMemoryModelRegistry(jsonMapper)
        val seeder = seeder(registry, seedJson())

        assertEquals(SeedOutcome(restored = 1), seeder.seed())
        assertEquals(SeedOutcome(leftAlone = 1), seeder.seed())
    }

    @Test
    fun `a missing seed file is not an error`() {
        assertEquals(SeedOutcome(), seeder(InMemoryModelRegistry(jsonMapper), null).seed())
    }

    @Test
    fun `seeding can be switched off`() {
        val registry = InMemoryModelRegistry(jsonMapper)

        val outcome = seeder(registry, seedJson(), ModelSeedDatafill(enabled = false)).seed()

        assertEquals(SeedOutcome(), outcome)
        assertEquals(0, registry.all().size)
    }

    @Test
    fun `a fingerprint that is not the hash of its structure is refused`() {
        val registry = InMemoryModelRegistry(jsonMapper)

        val failure = assertFailsWith<IllegalArgumentException> {
            seeder(registry, seedJson(fp = "0".repeat(64))).seed()
        }

        assertTrue(failure.message!!.contains("No reading would ever match it"), failure.message!!)
        assertEquals(0, registry.all().size)
    }

    @Test
    fun `a mapping naming a field the structure does not have is refused`() {
        val registry = InMemoryModelRegistry(jsonMapper)

        val failure = assertFailsWith<IllegalStateException> {
            seeder(
                registry,
                seedJson(sensors = """[{"name":"not_a_field","type":"ANALOG","classname":"temperature"}]"""),
            ).seed()
        }

        assertTrue(failure.message!!.contains("not a field in this model's structure"), failure.message!!)
        assertEquals(0, registry.all().size)
    }

    @Test
    fun `an exported model, extra computed fields included, reads back`() {
        val registry = InMemoryModelRegistry(jsonMapper)
        // `recognised` is computed and appears in GET /api/models output.
        val exported = """[{"fingerprint":"$fingerprint","source":"rtl433","name":"Acurite-Tower",
            "structure":${jsonMapper.writeValueAsString(structure)},
            "discoveredAt":"2026-10-09T10:00:00Z","recognised":true,
            "sensors":[{"name":"humidity","type":"ANALOG","classname":"humidity","friendlyName":"Humidity","inverted":false}]}]"""

        val outcome = seeder(registry, exported).seed()

        assertEquals(SeedOutcome(restored = 1), outcome)
        assertEquals(listOf("humidity"), registry.find(fingerprint)!!.sensors.map { it.name })
    }
}
