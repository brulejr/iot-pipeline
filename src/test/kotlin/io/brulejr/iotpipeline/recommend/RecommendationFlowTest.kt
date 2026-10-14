/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.recommend

import io.brulejr.iotpipeline.ingest.SensorEnvelope
import io.brulejr.iotpipeline.pipeline.PipelineChannels
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.MessageBuilder
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import kotlin.test.assertEquals

/**
 * Drives a reading from an unpromoted device through the whole pipeline and checks it is
 * counted. In-memory registries keep the context free of a database.
 */
@SpringBootTest(
    properties = [
        "pipeline.ingest.mqtt.enabled=false",
        "pipeline.classification.registry=memory",
        "pipeline.promotion.registry=memory",
        "pipeline.recommendation.registry=memory",
        // Readings are parsed and then dropped, so no InfluxDB is needed.
        "pipeline.store.enabled=false",
        // Nothing is published, so no broker is needed or reached.
        "pipeline.publish.enabled=false",
        // Nowhere: 27017 may be a real MongoDB, and nothing here should reach it.
        "spring.mongodb.uri=mongodb://127.0.0.1:1/unused",
    ],
)
class RecommendationFlowTest {

    @Autowired
    @Qualifier(PipelineChannels.INGEST)
    lateinit var ingest: MessageChannel

    @Autowired
    lateinit var sightings: SightingRegistryPort

    @Autowired
    lateinit var jsonMapper: JsonMapper

    @Test
    fun `a reading from an unpromoted device is counted with its signal`() {
        // An id no other test uses: the context, and so the registry, is shared.
        val payload = """{"model":"Nexus-TH","id":4242,"channel":"C","temperature_C":3.0,"snr":9.5}"""
        ingest.send(
            MessageBuilder.withPayload(
                SensorEnvelope("rtl433", "rtl_433/pi/events", Instant.now(), jsonMapper.readTree(payload)),
            ).build(),
        )

        val bucket = sightings.bucketsSince(Instant.EPOCH).single { it.deviceKey.id == "Nexus-TH/C/4242" }

        assertEquals(1, bucket.count)
        assertEquals(9.5, bucket.meanSignal)
        assertEquals("Nexus-TH", bucket.modelName)
    }
}
