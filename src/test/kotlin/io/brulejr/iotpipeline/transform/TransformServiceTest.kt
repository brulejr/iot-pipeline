/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.transform

import io.brulejr.iotpipeline.classify.DeviceKey
import io.brulejr.iotpipeline.classify.ModelRecord
import io.brulejr.iotpipeline.parse.ParsedReading
import io.brulejr.iotpipeline.promote.PromotedDevice
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransformServiceTest {

    private val now = Instant.parse("2026-10-12T12:00:00Z")

    private fun reading(id: String) = ParsedReading(
        device = PromotedDevice(DeviceKey("rtl433", id), "Sensor $id", "thermometer", "garden", now),
        model = ModelRecord("fp", "rtl433", "Acurite-Tower", "{}", now, "weather"),
        receivedAt = now,
        values = emptyList(),
    )

    /** One retained config message and one state message per device, the state numbered. */
    private var sequence = 0
    private val transformer = ReadingTransformerPort { reading ->
        val id = reading.device.deviceKey.id
        listOf(
            OutboundMessage("config/$id", "config", retained = true),
            OutboundMessage("state/$id", "state ${++sequence}", retained = false),
        )
    }

    @Test
    fun `the latest message for each topic is kept`() {
        val service = TransformService(transformer, TransformDatafill())

        service.transform(reading("a"))
        service.transform(reading("a"))
        service.transform(reading("b"))

        assertEquals(
            listOf("config/a" to "config", "config/b" to "config", "state/a" to "state 2", "state/b" to "state 3"),
            service.latest().map { it.topic to it.payload },
        )
        assertEquals(TransformStats(readings = 3, messages = 6, topics = 4), service.stats())
    }

    @Test
    fun `nothing is produced when transformation is off`() {
        val service = TransformService(transformer, TransformDatafill(enabled = false))

        assertTrue(service.transform(reading("a")).isEmpty())
        assertTrue(service.latest().isEmpty())
    }
}
