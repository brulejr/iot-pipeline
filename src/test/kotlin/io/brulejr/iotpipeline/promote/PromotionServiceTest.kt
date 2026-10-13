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
import kotlin.test.assertIs

class PromotionServiceTest {

    private val key = DeviceKey("rtl433", "Acurite-Tower/A/3064")

    private fun device(
        deviceKey: DeviceKey = key,
        name: String = "Back garden sensor",
        type: String = "thermometer",
        area: String = "garden",
    ) = PromotedDevice(deviceKey, name, type, area, Instant.now())

    @Test
    fun `a valid promotion is stored`() {
        val registry = InMemoryPromotionRegistry()

        assertIs<PromotionResult.Promoted>(PromotionService(registry).promote(device()))

        assertEquals("Back garden sensor", registry.find(key)!!.name)
    }

    @Test
    fun `every blank field is reported together`() {
        val registry = InMemoryPromotionRegistry()
        val service = PromotionService(registry)

        val result = service.promote(device(name = "", type = " ", area = ""))

        val rejected = assertIs<PromotionResult.Invalid>(result)
        assertEquals(listOf("name", "type", "area"), rejected.problems.map { it.field })
        assertEquals(0, registry.all().size)
    }

    @Test
    fun `a blank device key is rejected`() {
        val registry = InMemoryPromotionRegistry()
        val service = PromotionService(registry)

        val result = service.promote(device(deviceKey = DeviceKey("", "")))

        val rejected = assertIs<PromotionResult.Invalid>(result)
        assertEquals(listOf("source", "deviceId"), rejected.problems.map { it.field })
    }
}
