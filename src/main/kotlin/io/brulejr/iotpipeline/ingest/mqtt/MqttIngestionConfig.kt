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
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.handler.LoggingHandler
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory
import org.springframework.integration.mqtt.core.MqttPahoClientFactory
import org.springframework.integration.mqtt.event.MqttIntegrationEvent
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter
import org.springframework.integration.mqtt.support.MqttHeaders
import org.springframework.messaging.Message
import tools.jackson.databind.json.JsonMapper
import java.time.Clock

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
    fun mqttIngestionService(jsonMapper: JsonMapper) = MqttIngestionService(datafill, jsonMapper, Clock.systemUTC())

    @Bean
    fun mqttIngestionHealthIndicator(service: MqttIngestionService) = MqttIngestionHealthIndicator(service, datafill)

    @Bean
    fun mqttIngestionInfoContributor() = MqttIngestionInfoContributor(datafill)

    /** An anonymous class rather than a lambda, so Spring can see which events it takes. */
    @Bean
    fun mqttConnectionListener(service: MqttIngestionService) = object : ApplicationListener<MqttIntegrationEvent> {
        override fun onApplicationEvent(event: MqttIntegrationEvent) = service.onEvent(event)
    }

    @Bean
    fun mqttIngestionFlow(adapter: MqttPahoMessageDrivenChannelAdapter, service: MqttIngestionService) =
        integrationFlow(adapter) {
            transform<Message<String>> { message ->
                service.envelope(message.headers[MqttHeaders.RECEIVED_TOPIC] as String, message.payload)
            }
            // The raw payload, which later stages' log lines refer back to.
            log<SensorEnvelope>(LoggingHandler.Level.DEBUG, "pipeline.ingest") { message ->
                "received from ${message.payload.origin}: ${message.payload.payload}"
            }
            channel(PipelineChannels.INGEST)
        }
}
