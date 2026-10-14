/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.publish.mqtt

import io.brulejr.iotpipeline.publish.PublishDatafill
import io.brulejr.iotpipeline.publish.PublisherPort
import io.brulejr.iotpipeline.transform.OutboundMessage
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.nio.charset.StandardCharsets

/**
 * Publishes over MQTT with a plain Paho client, connecting on demand.
 *
 * Paho's own automatic reconnection is off: [io.brulejr.iotpipeline.publish.PublishService]
 * decides when to try again, and a client reconnecting on its own schedule would only
 * hide from it whether the broker is there. Nothing is persisted by the client either;
 * what failed is held by the service.
 */
class MqttPublisher(private val datafill: PublishDatafill.Mqtt) : PublisherPort, AutoCloseable {

    private val client = MqttClient(datafill.url, datafill.clientId, MemoryPersistence()).apply {
        timeToWait = datafill.completionTimeout.toMillis()
    }

    private val options = MqttConnectOptions().apply {
        isAutomaticReconnect = false
        isCleanSession = true
        connectionTimeout = datafill.connectTimeout.seconds.toInt()
        datafill.username?.let { userName = it }
        datafill.password?.let { password = it.toCharArray() }
    }

    @Synchronized
    override fun publish(message: OutboundMessage) {
        if (!client.isConnected) client.connect(options)
        client.publish(message.topic, message.payload.toByteArray(StandardCharsets.UTF_8), datafill.qos, message.retained)
    }

    @Synchronized
    override fun close() {
        runCatching { if (client.isConnected) client.disconnect() }
        client.close()
    }
}
