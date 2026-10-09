/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify

import io.brulejr.iotpipeline.ingest.SensorEnvelope
import io.brulejr.iotpipeline.pipeline.PipelineChannels
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.integration.channel.AbstractMessageChannel
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageBuilder
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs

@SpringBootTest(properties = ["pipeline.ingest.mqtt.enabled=false"])
class ClassificationFlowTest {

    @Autowired
    @Qualifier(PipelineChannels.INGEST)
    lateinit var ingest: MessageChannel

    @Autowired
    @Qualifier(PipelineChannels.UNKNOWN_DEVICE)
    lateinit var unknownDevice: AbstractMessageChannel

    @Autowired
    lateinit var jsonMapper: JsonMapper

    /** Sends one envelope through the flow and returns what reached the unknown channel. */
    private fun classifyThroughFlow(json: String): Classification {
        val captured = mutableListOf<Message<*>>()
        val capture = object : ChannelInterceptor {
            override fun preSend(message: Message<*>, channel: MessageChannel) = message.also { captured += it }
        }
        unknownDevice.addInterceptor(capture)
        try {
            val payload = jsonMapper.readTree(json)
            ingest.send(MessageBuilder.withPayload(SensorEnvelope("rtl433", "rtl_433/pi/events", Instant.now(), payload)).build())
        } finally {
            unknownDevice.removeInterceptor(capture)
        }
        return captured.single().payload as Classification
    }

    @Test
    fun `a curated model is identified but still routed for recommendation until promoted`() {
        val classification = assertIs<Classification.Identified>(
            classifyThroughFlow(
                """{"time":"2026-10-07 20:00:00","model":"Acurite-Tower","id":1234,"channel":"A","temperature_C":21.5,"humidity":48}""",
            ),
        )

        assertEquals("Acurite-Tower", classification.model)
        assertEquals("acurite-tower-v1", classification.parseRuleSetId)
        assertEquals(DeviceKey("rtl433", "Acurite-Tower/A/1234"), classification.deviceKey)
    }

    @Test
    fun `an uncurated model is routed to the unknown-device channel unidentified`() {
        val classification = assertIs<Classification.Unidentified>(
            classifyThroughFlow("""{"time":"2026-10-07 20:00:00","model":"Nexus-TH","id":77,"channel":"C","temperature_C":3.0}"""),
        )

        assertEquals(DeviceKey("rtl433", "Nexus-TH/C/77"), classification.deviceKey)
    }
}
