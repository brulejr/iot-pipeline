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
import io.brulejr.iotpipeline.pipeline.PipelineChannels
import io.brulejr.iotpipeline.promote.PromotedDevice
import io.brulejr.iotpipeline.promote.PromotionService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.integration.channel.AbstractMessageChannel
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.ChannelInterceptor
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(
    properties = [
        "pipeline.ingest.mqtt.enabled=false",
        // Keeps the context free of a database. The datasource is still configured, but
        // with in-memory registries and storage off nothing ever connects.
        "pipeline.classification.registry=memory",
        "pipeline.promotion.registry=memory",
        "pipeline.recommendation.registry=memory",
        // Readings are parsed and then dropped, so no database is needed.
        "pipeline.store.enabled=false",
        // Nothing is published, so no broker is needed or reached.
        "pipeline.publish.enabled=false",
        // Nowhere: 5432 may be a real PostgreSQL, and nothing here should reach it.
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unused",
    ],
)
class DemotionFlowTest {

    @Autowired
    lateinit var promotions: PromotionService

    @Autowired
    @Qualifier(PipelineChannels.OUTBOUND)
    lateinit var outbound: AbstractMessageChannel

    private val key = DeviceKey("rtl433", "Acurite-Tower/A/demotionFlow")

    /** What reaches publication while [action] runs. */
    private fun captureOutbound(action: () -> Unit): List<OutboundMessage> {
        val captured = mutableListOf<Message<*>>()
        val capture = object : ChannelInterceptor {
            override fun preSend(message: Message<*>, channel: MessageChannel) = message.also { captured += it }
        }
        outbound.addInterceptor(capture)
        try {
            action()
        } finally {
            outbound.removeInterceptor(capture)
        }
        return captured.map { it.payload as OutboundMessage }
    }

    @Test
    fun `demoting a device withdraws it from Home Assistant`() {
        promotions.promote(PromotedDevice(key, "Shed sensor", "thermometer", "shed", Instant.now()))

        val messages = captureOutbound { promotions.demote(key) }

        assertEquals(
            listOf(
                "homeassistant/device/rtl433_acurite_tower_a_demotionflow/config",
                "iot-pipeline/rtl433_acurite_tower_a_demotionflow/state",
            ),
            messages.map { it.topic },
        )
        assertTrue(messages.all { it.payload.isEmpty() && it.retained })
    }

    @Test
    fun `demoting a device that was never promoted sends nothing`() {
        val messages = captureOutbound { promotions.demote(DeviceKey("rtl433", "never/promoted")) }

        assertTrue(messages.isEmpty())
    }
}
