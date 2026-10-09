/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.pipeline

import io.brulejr.iotpipeline.classify.Classification
import io.brulejr.iotpipeline.promote.PromotedReading
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.handler.LoggingHandler

/**
 * Placeholder endpoints for stages that are not built yet, so every channel has a
 * subscriber. Replace each flow as its stage is implemented.
 */
@Configuration
class StageStubFlows {

    // TODO storage stage: parse these with their model's sensor mappings and write them
    // to InfluxDB.
    @Bean
    fun knownDeviceStubFlow() = integrationFlow(PipelineChannels.KNOWN_DEVICE) {
        log<PromotedReading>(LoggingHandler.Level.INFO, "pipeline.known") { message ->
            val reading = message.payload
            val model = reading.classification.model
            "${reading.device.name} in ${reading.device.area} " +
                "(${reading.classification.deviceKey?.id}) as ${model.category}/" +
                "${model.name ?: "unnamed"}, ${model.sensors.size} mapping(s): " +
                "${reading.classification.reading.envelope.payload}"
        }
        nullChannel()
    }

    // TODO recommendation engine: track devices by frequency and proximity and
    // recommend the worthwhile ones for promotion.
    @Bean
    fun unknownDeviceStubFlow() = integrationFlow(PipelineChannels.UNKNOWN_DEVICE) {
        // Only promotion status: the model and the payload are already on the
        // pipeline.classify and pipeline.ingest lines for this same reading.
        log<Classification>(LoggingHandler.Level.DEBUG, "pipeline.unknown") { message ->
            val classification = message.payload
            "awaiting promotion: ${classification.deviceKey?.id ?: "unidentified device"} " +
                "from ${classification.reading.envelope.origin}"
        }
        nullChannel()
    }

    @Bean
    fun ingestErrorFlow() = integrationFlow(PipelineChannels.INGEST_ERRORS) {
        log(LoggingHandler.Level.WARN, "pipeline.errors")
        nullChannel()
    }
}
