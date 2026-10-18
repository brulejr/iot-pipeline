/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote.postgres

import io.brulejr.iotpipeline.classify.DeviceKey
import io.brulejr.iotpipeline.pipeline.PostgresTestDatabase
import io.brulejr.iotpipeline.promote.PromotedDevice
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers(disabledWithoutDocker = true)
class PostgresPromotionRegistryTest {

    private val registry = PostgresPromotionRegistry(PostgresTestDatabase.jdbc, PostgresTestDatabase.transactions)

    private val door = DeviceKey("rtl433", "DSC-Security/2256987")
    private val tower = DeviceKey("rtl433", "Acurite-Tower/A/3064")
    private val at = Instant.parse("2026-10-18T10:00:00Z")

    @BeforeEach
    fun reset() = PostgresTestDatabase.reset()

    private fun device(key: DeviceKey = door, name: String = "Kitchen Sliding Door", area: String = "kitchen") =
        PromotedDevice(key, name, "door", area, at)

    @Test
    fun `a promotion reads back whole, slashes in the id and all`() {
        registry.save(device())

        assertEquals(device(), registry.find(door))
    }

    @Test
    fun `promoting again replaces the labels`() {
        registry.save(device())

        registry.save(device(name = "Patio Door", area = "patio"))

        assertEquals(1, registry.all().size)
        assertEquals("Patio Door", registry.find(door)!!.name)
    }

    @Test
    fun `a device heard through another source is a different device`() {
        registry.save(device())

        assertNull(registry.find(DeviceKey("weather-station-rest", door.id)))
    }

    @Test
    fun `demoting removes the promotion`() {
        registry.save(device())

        assertTrue(registry.demote(door))

        assertNull(registry.find(door))
        assertFalse(registry.demote(door))
    }

    @Test
    fun `saving several stores them all`() {
        registry.saveAll(listOf(device(), device(tower, "Lab Climate", "lab")))

        assertEquals(setOf(door, tower), registry.all().map { it.deviceKey }.toSet())
    }

    @Test
    fun `saving several is all or none`() {
        registry.save(device(tower, "Lab Climate", "lab"))
        // Breaks the second insert from inside the database, after the first succeeded.
        val broken = device(DeviceKey("rtl433", "x".repeat(10))).copy(name = "\u0000")

        assertThrows<Exception> { registry.saveAll(listOf(device(), broken)) }

        assertNull(registry.find(door), "the first promotion was rolled back")
        assertEquals("Lab Climate", registry.find(tower)!!.name, "what was there before is untouched")
    }
}
