/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.ingest.mqtt

import org.junit.jupiter.api.Test
import org.springframework.integration.mqtt.event.MqttConnectionFailedEvent
import org.springframework.integration.mqtt.event.MqttSubscribedEvent
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MqttIngestionServiceTest {

    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private val service = MqttIngestionService(
        MqttIngestionDatafill(source = "rtl433"),
        JsonMapper.builder().build(),
        Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `a message becomes an envelope stamped with the source and topic`() {
        val envelope = service.envelope("rtl_433/pi/events", """{"model":"Acurite-Tower","id":1}""")

        assertEquals("rtl433", envelope.source)
        assertEquals("rtl_433/pi/events", envelope.origin)
        assertEquals(now, envelope.receivedAt)
        assertEquals("Acurite-Tower", envelope.payload.get("model").asString())
        assertEquals(1, service.status().received)
        assertEquals(now, service.status().lastReceivedAt)
    }

    @Test
    fun `a body that is not JSON is counted and rethrown`() {
        assertFailsWith<JacksonException> { service.envelope("rtl_433/pi/events", "not json {") }

        assertEquals(1, service.status().malformed)
        assertEquals(0, service.status().received)
    }

    @Test
    fun `the connection is unknown until the adapter reports`() {
        assertNull(service.status().connected)
    }

    @Test
    fun `a subscription connects and a failure disconnects`() {
        service.onEvent(MqttSubscribedEvent(this, "subscribed"))
        assertTrue(service.status().connected!!)

        service.onEvent(MqttConnectionFailedEvent(this, IllegalStateException("broker went away")))

        assertFalse(service.status().connected!!)
        assertEquals("broker went away", service.status().lastFailure)
    }
}
