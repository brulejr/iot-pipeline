/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote.seed

import io.brulejr.iotpipeline.classify.DeviceKey
import io.brulejr.iotpipeline.promote.InMemoryPromotionRegistry
import io.brulejr.iotpipeline.promote.PromotedDevice
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

class PromotionSeederTest {

    // The Kotlin module makes an omitted field fall back to its default, as it does in
    // the application where Spring Boot registers it.
    private val jsonMapper = JsonMapper.builder().addModule(kotlinModule()).build()

    private val key = DeviceKey("rtl433", "Acurite-Tower/A/3064")

    private fun seedJson(
        source: String = "rtl433",
        deviceId: String = "Acurite-Tower/A/3064",
        name: String = "Back garden sensor",
    ) = """[{"deviceKey":{"source":"$source","id":"$deviceId"},
            "name":"$name","type":"thermometer","area":"garden"}]"""

    private fun loaderOf(json: String?) = object : ResourceLoader {
        override fun getResource(location: String): Resource = json
            ?.let { ByteArrayResource(it.toByteArray()) }
            ?: ClassPathResource("no-such-promotion-seed.json")

        override fun getClassLoader(): ClassLoader = javaClass.classLoader
    }

    private fun seeder(
        registry: InMemoryPromotionRegistry,
        json: String?,
        datafill: PromotionSeedDatafill = PromotionSeedDatafill(),
    ) = PromotionSeeder(registry, datafill, jsonMapper, loaderOf(json))

    @Test
    fun `a device the store has no approval for is restored`() {
        val registry = InMemoryPromotionRegistry()

        val outcome = seeder(registry, seedJson()).seed()

        assertEquals(PromotionSeedOutcome(restored = 1), outcome)
        val restored = registry.find(key)!!
        assertEquals("Back garden sensor", restored.name)
        assertEquals("garden", restored.area)
    }

    @Test
    fun `an approval already in the store is never overwritten`() {
        val registry = InMemoryPromotionRegistry()
        registry.promote(PromotedDevice(key, "Shed sensor", "thermometer", "shed", Instant.now()))

        val outcome = seeder(registry, seedJson()).seed()

        assertEquals(PromotionSeedOutcome(leftAlone = 1), outcome)
        // The label corrected in the running system survives, not the file's.
        assertEquals("Shed sensor", registry.find(key)!!.name)
    }

    @Test
    fun `seeding twice changes nothing the second time`() {
        val registry = InMemoryPromotionRegistry()
        val seeder = seeder(registry, seedJson())

        assertEquals(PromotionSeedOutcome(restored = 1), seeder.seed())
        assertEquals(PromotionSeedOutcome(leftAlone = 1), seeder.seed())
    }

    @Test
    fun `a missing seed file is not an error`() {
        assertEquals(PromotionSeedOutcome(), seeder(InMemoryPromotionRegistry(), null).seed())
    }

    @Test
    fun `seeding can be switched off`() {
        val registry = InMemoryPromotionRegistry()

        val outcome = seeder(registry, seedJson(), PromotionSeedDatafill(enabled = false)).seed()

        assertEquals(PromotionSeedOutcome(), outcome)
        assertEquals(0, registry.all().size)
    }

    @Test
    fun `an entry with a blank label is refused`() {
        val registry = InMemoryPromotionRegistry()

        val failure = assertFailsWith<IllegalStateException> {
            seeder(registry, seedJson(name = "")).seed()
        }

        assertTrue(failure.message!!.contains("name: must not be blank"), failure.message!!)
        assertEquals(0, registry.all().size)
    }

    @Test
    fun `an entry with a blank device key is refused`() {
        val registry = InMemoryPromotionRegistry()

        assertFailsWith<IllegalStateException> {
            seeder(registry, seedJson(source = "", deviceId = "")).seed()
        }

        assertEquals(0, registry.all().size)
    }

    @Test
    fun `an exported approval, promotedAt included, reads back`() {
        val registry = InMemoryPromotionRegistry()
        val exported = """[{"deviceKey":{"source":"rtl433","id":"Acurite-Tower/A/3064"},
            "name":"Back garden sensor","type":"thermometer","area":"garden",
            "promotedAt":"2026-10-09T21:04:37.465669212Z"}]"""

        val outcome = seeder(registry, exported).seed()

        assertEquals(PromotionSeedOutcome(restored = 1), outcome)
        assertEquals("Back garden sensor", registry.find(key)!!.name)
    }
}
