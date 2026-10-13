/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.ingest.mqtt

import io.brulejr.iotpipeline.ingest.SensorEnvelope
import org.springframework.integration.mqtt.event.MqttConnectionFailedEvent
import org.springframework.integration.mqtt.event.MqttIntegrationEvent
import org.springframework.integration.mqtt.event.MqttSubscribedEvent
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Stage 1: wraps each MQTT message in a [SensorEnvelope], and keeps track of the broker
 * connection so a silent pipeline can be told apart from a disconnected one.
 */
class MqttIngestionService(
    private val datafill: MqttIngestionDatafill,
    private val jsonMapper: JsonMapper,
    private val clock: Clock,
) {

    private val received = AtomicLong()
    private val malformed = AtomicLong()
    private val lastReceivedAt = AtomicReference<Instant?>()
    private val connected = AtomicReference<Boolean?>()
    private val lastFailure = AtomicReference<String?>()

    /**
     * The envelope for one message received on [topic]. A body that is not JSON is
     * counted and rethrown, so it reaches the ingest error log.
     */
    fun envelope(topic: String, body: String): SensorEnvelope {
        val payload = try {
            jsonMapper.readTree(body)
        } catch (e: JacksonException) {
            malformed.incrementAndGet()
            throw e
        }
        val now = clock.instant()
        received.incrementAndGet()
        lastReceivedAt.set(now)
        return SensorEnvelope(source = datafill.source, origin = topic, receivedAt = now, payload = payload)
    }

    /**
     * Follows the adapter's connection. A subscription means it is connected and
     * listening; a failure is published both when connecting fails and when an
     * established connection is lost, and automatic reconnection subscribes again.
     */
    fun onEvent(event: MqttIntegrationEvent) {
        when (event) {
            is MqttSubscribedEvent -> connected.set(true)
            is MqttConnectionFailedEvent -> {
                connected.set(false)
                lastFailure.set(event.cause?.message ?: event.cause?.javaClass?.simpleName ?: "connection failed")
            }
            else -> Unit
        }
    }

    fun status() = MqttIngestionStatus(
        connected = connected.get(),
        received = received.get(),
        malformed = malformed.get(),
        lastReceivedAt = lastReceivedAt.get(),
        lastFailure = lastFailure.get(),
    )
}
