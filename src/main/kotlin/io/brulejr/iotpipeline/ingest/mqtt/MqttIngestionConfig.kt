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
import io.brulejr.iotpipeline.pipeline.PipelineChannels
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.handler.LoggingHandler
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory
import org.springframework.integration.mqtt.core.MqttPahoClientFactory
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter
import org.springframework.integration.mqtt.support.MqttHeaders
import org.springframework.messaging.Message
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

/**
 * MQTT ingestion adapter: subscribes to the rtl_433 topic(s), wraps each JSON
 * message in a [SensorEnvelope] and hands it to [PipelineChannels.INGEST].
 */
@Configuration
@ConditionalOnBooleanProperty("pipeline.ingest.mqtt.enabled", matchIfMissing = true)
class MqttIngestionConfig(private val datafill: MqttIngestionDatafill) {

    @Bean
    fun mqttClientFactory(): MqttPahoClientFactory = DefaultMqttPahoClientFactory().apply {
        connectionOptions = MqttConnectOptions().apply {
            serverURIs = arrayOf(datafill.url)
            isAutomaticReconnect = true
            datafill.username?.let { userName = it }
            datafill.password?.let { password = it.toCharArray() }
        }
    }

    @Bean
    fun mqttInboundAdapter(clientFactory: MqttPahoClientFactory) =
        MqttPahoMessageDrivenChannelAdapter(datafill.clientId, clientFactory, *datafill.topics.toTypedArray()).apply {
            setQos(datafill.qos)
            setErrorChannelName(PipelineChannels.INGEST_ERRORS)
        }

    @Bean
    fun mqttIngestionFlow(adapter: MqttPahoMessageDrivenChannelAdapter, jsonMapper: JsonMapper) =
        integrationFlow(adapter) {
            transform<Message<String>> { message ->
                SensorEnvelope(
                    source = datafill.source,
                    origin = message.headers[MqttHeaders.RECEIVED_TOPIC] as String,
                    receivedAt = Instant.now(),
                    payload = jsonMapper.readTree(message.payload),
                )
            }
            // Logs are the only record of a reading until the storage stage lands.
            log<SensorEnvelope>(LoggingHandler.Level.DEBUG, "pipeline.ingest") { message ->
                "received from ${message.payload.origin}: ${message.payload.payload}"
            }
            channel(PipelineChannels.INGEST)
        }
}
