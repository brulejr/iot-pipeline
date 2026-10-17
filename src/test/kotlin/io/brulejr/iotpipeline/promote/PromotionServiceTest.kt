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
import kotlin.test.assertTrue

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

    @Test
    fun `demoting a promoted device tells consumers it is gone`() {
        val registry = InMemoryPromotionRegistry()
        val demoted = mutableListOf<DeviceKey>()
        val service = PromotionService(registry) { demoted += it }
        service.promote(device())

        assertTrue(service.demote(key))

        assertEquals(listOf(key), demoted)
    }

    @Test
    fun `demoting a device that was never promoted tells nobody`() {
        val demoted = mutableListOf<DeviceKey>()
        val service = PromotionService(InMemoryPromotionRegistry()) { demoted += it }

        assertFalse(service.demote(key))

        assertTrue(demoted.isEmpty())
    }

    private val door = DeviceKey("rtl433", "DSC-Security/2256987")

    @Test
    fun `a bulk promotion stores every device`() {
        val registry = InMemoryPromotionRegistry()

        val result = PromotionService(registry).promoteAll(
            listOf(device(), device(deviceKey = door, name = "Kitchen door")),
        )

        assertEquals(2, assertIs<BulkPromotionResult.Promoted>(result).promoted.size)
        assertEquals("Kitchen door", registry.find(door)!!.name)
    }

    @Test
    fun `a bulk promotion reports devices already promoted as updated`() {
        val registry = InMemoryPromotionRegistry()
        val service = PromotionService(registry)
        service.promote(device())

        val result = service.promoteAll(
            listOf(device(name = "Renamed sensor"), device(deviceKey = door, name = "Kitchen door")),
        )

        val stored = assertIs<BulkPromotionResult.Promoted>(result)
        assertEquals(listOf(door), stored.promoted.map { it.deviceKey })
        assertEquals(listOf(key), stored.updated.map { it.deviceKey })
        assertEquals("Renamed sensor", registry.find(key)!!.name)
    }

    @Test
    fun `one invalid entry refuses the whole bulk promotion`() {
        val registry = InMemoryPromotionRegistry()

        val result = PromotionService(registry).promoteAll(
            listOf(device(), device(deviceKey = door, name = " ", area = "")),
        )

        val rejected = assertIs<BulkPromotionResult.Invalid>(result)
        assertEquals(listOf("[1].name", "[1].area"), rejected.problems.map { it.field })
        assertEquals(0, registry.all().size)
    }

    @Test
    fun `a device named twice refuses the bulk promotion`() {
        val registry = InMemoryPromotionRegistry()

        val result = PromotionService(registry).promoteAll(
            listOf(device(), device(deviceKey = door), device(name = "Other name")),
        )

        val rejected = assertIs<BulkPromotionResult.Invalid>(result)
        assertEquals(listOf(PromotionProblem("[2].deviceKey", "repeats entry [0]")), rejected.problems)
        assertEquals(0, registry.all().size)
    }
}
