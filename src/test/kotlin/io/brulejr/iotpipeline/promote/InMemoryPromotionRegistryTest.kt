/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote

import io.brulejr.iotpipeline.classify.DeviceKey
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryPromotionRegistryTest {

    private val key = DeviceKey("rtl433", "Acurite-Tower/A/3064")

    private fun device(
        deviceKey: DeviceKey = key,
        name: String = "Back garden sensor",
        type: String = "thermometer",
        area: String = "garden",
    ) = PromotedDevice(deviceKey, name, type, area, Instant.now())

    @Test
    fun `an unpromoted device is not found`() {
        assertNull(InMemoryPromotionRegistry().find(key))
    }

    @Test
    fun `a promoted device is found by its key`() {
        val registry = InMemoryPromotionRegistry()

        assertIs<PromotionResult.Promoted>(registry.promote(device()))

        assertEquals("Back garden sensor", registry.find(key)!!.name)
    }

    @Test
    fun `promoting again replaces the labels`() {
        val registry = InMemoryPromotionRegistry()
        registry.promote(device())

        registry.promote(device(name = "Shed sensor", area = "shed"))

        assertEquals(1, registry.all().size)
        assertEquals("Shed sensor", registry.find(key)!!.name)
    }

    @Test
    fun `promotion is per device, not per model`() {
        val registry = InMemoryPromotionRegistry()
        registry.promote(device())

        // A neighbour's identical model, different id.
        assertNull(registry.find(DeviceKey("rtl433", "Acurite-Tower/A/9981")))
    }

    @Test
    fun `a device heard through another source is a different device`() {
        val registry = InMemoryPromotionRegistry()
        registry.promote(device())

        assertNull(registry.find(DeviceKey("weather-station-rest", "Acurite-Tower/A/3064")))
    }

    @Test
    fun `demoting removes the promotion`() {
        val registry = InMemoryPromotionRegistry()
        registry.promote(device())

        assertTrue(registry.demote(key))

        assertNull(registry.find(key))
        assertFalse(registry.demote(key))
    }

    @Test
    fun `every blank field is reported together`() {
        val registry = InMemoryPromotionRegistry()

        val result = registry.promote(device(name = "", type = " ", area = ""))

        val rejected = assertIs<PromotionResult.Invalid>(result)
        assertEquals(listOf("name", "type", "area"), rejected.problems.map { it.field })
        assertEquals(0, registry.all().size)
    }

    @Test
    fun `a blank device key is rejected`() {
        val registry = InMemoryPromotionRegistry()

        val result = registry.promote(device(deviceKey = DeviceKey("", "")))

        val rejected = assertIs<PromotionResult.Invalid>(result)
        assertEquals(listOf("source", "deviceId"), rejected.problems.map { it.field })
    }
}
