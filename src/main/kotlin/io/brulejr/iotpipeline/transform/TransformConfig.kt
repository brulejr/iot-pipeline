/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.transform

import io.brulejr.iotpipeline.parse.ParsedReading
import io.brulejr.iotpipeline.pipeline.PipelineChannels
import io.brulejr.iotpipeline.transform.homeassistant.HomeAssistantTransformer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.handler.LoggingHandler
import tools.jackson.databind.json.JsonMapper

/**
 * Stage 6: reshapes each stored reading into the messages a consumer expects.
 *
 * Reads from the stored channel, so a reading is only transformed once it has been
 * kept: storage is the neutral boundary and everything consumer-specific happens after
 * it. Publication is not built yet, so the messages end here, logged.
 */
@Configuration
class TransformConfig {

    @Bean
    fun readingTransformer(datafill: TransformDatafill, jsonMapper: JsonMapper): ReadingTransformerPort =
        HomeAssistantTransformer(datafill.homeAssistant, jsonMapper)

    @Bean
    fun transformService(transformer: ReadingTransformerPort, datafill: TransformDatafill) =
        TransformService(transformer, datafill)

    @Bean
    fun transformHealthIndicator(service: TransformService) = TransformHealthIndicator(service)

    @Bean
    fun transformInfoContributor(datafill: TransformDatafill) = TransformInfoContributor(datafill)

    @Bean
    fun transformFlow(service: TransformService) =
        integrationFlow(PipelineChannels.STORED) {
            transform<ParsedReading> { service.transform(it) }
            // One message per outbound item, so each can be published, and fail, on its own.
            split()
            log<OutboundMessage>(LoggingHandler.Level.DEBUG, "pipeline.transform") { message ->
                val outbound = message.payload
                "${outbound.topic}${if (outbound.retained) " (retained)" else ""}: ${outbound.payload}"
            }
            // TODO stage 7: publish these to MQTT.
            nullChannel()
        }
}
