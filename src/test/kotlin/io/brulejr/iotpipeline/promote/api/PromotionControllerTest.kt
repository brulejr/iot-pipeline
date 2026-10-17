/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote.api

import io.brulejr.iotpipeline.classify.DeviceKey
import io.brulejr.iotpipeline.promote.InMemoryPromotionRegistry
import io.brulejr.iotpipeline.promote.PromotedDevice
import io.brulejr.iotpipeline.promote.PromotionService
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Bulk promotion shares its path with the listing and sits beside `/{source}/{*deviceId}`,
 * so these go through Spring MVC's routing and JSON binding rather than calling the
 * controller.
 */
class PromotionControllerTest {

    private val registry = InMemoryPromotionRegistry()
    private val mvc: MockMvc =
        MockMvcBuilders.standaloneSetup(PromotionController(PromotionService(registry))).build()

    private val tower = DeviceKey("rtl433", "Acurite-Tower/A/3064")
    private val door = DeviceKey("rtl433", "DSC-Security/2256987")

    private fun postJson(body: String) = mvc.perform(
        post("/api/promoted-devices").contentType(MediaType.APPLICATION_JSON).content(body),
    )

    @Test
    fun `bulk promotion separates new promotions from updated ones, ignoring fields a listing carries`() {
        registry.save(PromotedDevice(tower, "Lab Weather sensor", "thermometer", "lab", Instant.now()))

        postJson(
            """
            [
              {"deviceKey": {"source": "rtl433", "id": "Acurite-Tower/A/3064"},
               "name": "Lab Weather sensor", "type": "thermometer", "area": "lab",
               "promotedAt": "2026-10-01T00:00:00Z"},
              {"deviceKey": {"source": "rtl433", "id": "DSC-Security/2256987"},
               "name": "Kitchen sliding door", "type": "contact", "area": "kitchen"}
            ]
            """,
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.promoted.length()").value(1))
            .andExpect(jsonPath("$.promoted[0].deviceKey.id").value("DSC-Security/2256987"))
            .andExpect(jsonPath("$.updated.length()").value(1))
            .andExpect(jsonPath("$.updated[0].deviceKey.id").value("Acurite-Tower/A/3064"))

        assertEquals("Kitchen sliding door", registry.find(door)!!.name)
    }

    @Test
    fun `an invalid bulk promotion is refused with each problem by index`() {
        postJson(
            """
            [
              {"deviceKey": {"source": "rtl433", "id": "DSC-Security/2256987"},
               "name": "", "type": "contact", "area": "kitchen"}
            ]
            """,
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.problems[0].field").value("[0].name"))

        assertNull(registry.find(door))
    }

    @Test
    fun `a single promotion still reaches its own path`() {
        mvc.perform(
            put("/api/promoted-devices/rtl433/DSC-Security/2256987")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Kitchen sliding door", "type": "contact", "area": "kitchen"}"""),
        ).andExpect(status().isOk)

        assertEquals("kitchen", registry.find(door)!!.area)
    }
}
