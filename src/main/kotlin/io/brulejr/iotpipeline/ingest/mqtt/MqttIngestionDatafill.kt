/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.ingest.mqtt

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("pipeline.ingest.mqtt")
data class MqttIngestionDatafill(
    val enabled: Boolean = true,
    val url: String = "tcp://localhost:1883",
    val clientId: String = "iot-pipeline",
    val username: String? = null,
    val password: String? = null,
    /** rtl_433 publishes all decoded events to `rtl_433/<hostname>/events` by default. */
    val topics: List<String> = listOf("rtl_433/+/events"),
    val qos: Int = 1,
    /** Source annotation stamped on every envelope from this adapter. */
    val source: String = "rtl433",
) {
    init {
        require(url.isNotBlank()) { "pipeline.ingest.mqtt.url must not be blank" }
        require(clientId.isNotBlank()) { "pipeline.ingest.mqtt.client-id must not be blank" }
        require(topics.isNotEmpty() && topics.none { it.isBlank() }) {
            "pipeline.ingest.mqtt.topics must name at least one topic, none of them blank"
        }
        require(qos in 0..2) { "pipeline.ingest.mqtt.qos must be 0, 1 or 2" }
        require(source.isNotBlank()) { "pipeline.ingest.mqtt.source must not be blank" }
    }
}
