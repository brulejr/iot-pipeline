/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.actuator

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every feature reports its health and describes itself on the info endpoint. */
@SpringBootTest(
    properties = [
        "pipeline.ingest.mqtt.enabled=false",
        // Keeps the context free of a database. The URI is still bound by Mongo's
        // auto-configuration, but with an in-memory registry nothing ever connects.
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
class ActuatorContributionTest {

    @Autowired
    lateinit var healthIndicators: Map<String, HealthIndicator>

    @Autowired
    lateinit var infoContributors: List<InfoContributor>

    /** MQTT ingestion is disabled here, so it contributes neither. */
    private val features = listOf(
        "fingerprint", "dedupe", "classification", "promotion", "recommendation", "parse", "store", "transform",
        "publish",
    )

    @Test
    fun `every feature is up`() {
        features.forEach { feature ->
            val indicator = healthIndicators["${feature}HealthIndicator"]
            assertTrue(indicator != null, "no health indicator for $feature")
            assertEquals("UP", indicator.health()?.status?.code, "$feature is not up")
        }
    }

    @Test
    fun `every feature describes itself`() {
        val builder = Info.Builder()
        infoContributors.forEach { it.contribute(builder) }
        val details = builder.build().details

        features.forEach { feature -> assertTrue(feature in details, "no info for $feature") }
    }
}
