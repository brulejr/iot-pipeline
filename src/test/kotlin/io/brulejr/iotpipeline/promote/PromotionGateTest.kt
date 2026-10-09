/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote

import io.brulejr.iotpipeline.classify.Classification
import io.brulejr.iotpipeline.classify.DeviceKey
import io.brulejr.iotpipeline.classify.ModelRegistryPort
import io.brulejr.iotpipeline.classify.SensorMapping
import io.brulejr.iotpipeline.classify.SensorType
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
import kotlin.test.assertTrue

/**
 * Drives readings through the whole pipeline to check the gate sends each one where it
 * belongs. In-memory registries keep the context free of a database.
 */
@SpringBootTest(
    properties = [
        "pipeline.ingest.mqtt.enabled=false",
        "pipeline.model-registry.type=memory",
        "pipeline.promotion-registry.type=memory",
        "spring.mongodb.uri=mongodb://localhost:27017/unused",
    ],
)
class PromotionGateTest {

    @Autowired
    @Qualifier(PipelineChannels.INGEST)
    lateinit var ingest: MessageChannel

    @Autowired
    @Qualifier(PipelineChannels.KNOWN_DEVICE)
    lateinit var knownDevice: AbstractMessageChannel

    @Autowired
    @Qualifier(PipelineChannels.UNKNOWN_DEVICE)
    lateinit var unknownDevice: AbstractMessageChannel

    @Autowired
    @Qualifier(PipelineChannels.PROMOTION_GAPS)
    lateinit var promotionGaps: AbstractMessageChannel

    @Autowired
    lateinit var models: ModelRegistryPort

    @Autowired
    lateinit var promotions: PromotionPort

    @Autowired
    lateinit var jsonMapper: JsonMapper

    /**
     * Each test works on its own model and device.
     *
     * The Spring context is cached and shared between test classes, so the in-memory
     * registries accumulate across them. A unique [marker] field gives each test its own
     * payload structure, and so its own model, whatever else has been registered.
     * Varying the temperature keeps dedupe from swallowing the second reading.
     */
    private fun reading(marker: String, temperature: Double) =
        """{"model":"Acurite-Tower","id":3064,"channel":"A","temperature_C":$temperature,
            "humidity":47,"$marker":1}""".trimIndent()

    private val deviceKey = DeviceKey("rtl433", "Acurite-Tower/A/3064")

    private fun send(json: String) {
        val envelope = SensorEnvelope("rtl433", "rtl_433/pi/events", Instant.now(), jsonMapper.readTree(json))
        ingest.send(MessageBuilder.withPayload(envelope).build())
    }

    /**
     * Registers this test's model by sending one reading, and returns it. Matches the
     * quoted field name, so one marker cannot match another that contains it.
     */
    private fun registerModel(marker: String) = run {
        send(reading(marker, 20.0))
        models.all().single { """"$marker"""" in it.structure }
    }

    /** Sends [json] and returns whatever reached [channel], if anything. */
    private fun capture(channel: AbstractMessageChannel, json: String): List<Message<*>> {
        val captured = mutableListOf<Message<*>>()
        val capture = object : ChannelInterceptor {
            override fun preSend(message: Message<*>, c: MessageChannel) = message.also { captured += it }
        }
        channel.addInterceptor(capture)
        try {
            send(json)
        } finally {
            channel.removeInterceptor(capture)
        }
        return captured
    }

    private fun curate(marker: String) {
        models.curate(
            registerModel(marker).fingerprint,
            "weather",
            listOf(SensorMapping("temperature_C", SensorType.ANALOG, "temperature")),
        )
    }

    private fun promote() {
        promotions.promote(
            PromotedDevice(deviceKey, "Back garden sensor", "thermometer", "garden", Instant.now()),
        )
    }

    @Test
    fun `an unpromoted device goes to the recommendation engine`() {
        curate("gateUnpromoted")
        promotions.demote(deviceKey)

        val captured = capture(unknownDevice, reading("gateUnpromoted", 21.0))

        // Recognised model, but nobody approved the device.
        val classification = assertIs<Classification.Recognised>(captured.single().payload)
        assertEquals(deviceKey, classification.deviceKey)
    }

    @Test
    fun `a promoted device with a curated model reaches the known-device channel`() {
        curate("gatePromoted")
        promote()

        val captured = capture(knownDevice, reading("gatePromoted", 22.0))

        val promoted = assertIs<PromotedReading>(captured.single().payload)
        assertEquals("Back garden sensor", promoted.device.name)
        assertEquals("garden", promoted.device.area)
        assertEquals("weather", promoted.classification.model.category)
    }

    @Test
    fun `a promoted device whose model is uncurated is reported as a gap`() {
        // Registers the model without curating it.
        registerModel("gateUncurated")
        promote()

        val captured = capture(promotionGaps, reading("gateUncurated", 24.0))

        val classification = assertIs<Classification.Unrecognised>(captured.single().payload)
        assertTrue(classification.model.sensors.isEmpty())
    }

    @Test
    fun `demoting sends the device back to the recommendation engine`() {
        curate("gateDemoted")
        promote()
        promotions.demote(deviceKey)

        val captured = capture(unknownDevice, reading("gateDemoted", 25.0))

        assertIs<Classification.Recognised>(captured.single().payload)
    }
}
