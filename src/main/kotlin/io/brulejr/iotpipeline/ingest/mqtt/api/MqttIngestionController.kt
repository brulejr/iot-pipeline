/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.ingest.mqtt.api

import io.brulejr.iotpipeline.ingest.mqtt.MqttIngestionService
import io.brulejr.iotpipeline.ingest.mqtt.MqttIngestionStatus
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The broker connection and what has arrived over it. Absent, like the rest of MQTT
 * ingestion, when `pipeline.ingest.mqtt.enabled` is false.
 */
@RestController
@RequestMapping("/api/ingest/mqtt")
@ConditionalOnBooleanProperty("pipeline.ingest.mqtt.enabled", matchIfMissing = true)
class MqttIngestionController(private val service: MqttIngestionService) {

    @GetMapping
    fun status(): MqttIngestionStatus = service.status()
}
