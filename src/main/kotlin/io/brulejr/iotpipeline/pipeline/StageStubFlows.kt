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

    // TODO storage stage: parse promoted readings with their model's rule set and
    // write them to InfluxDB. Nothing reaches this channel until promotion exists.
    @Bean
    fun knownDeviceStubFlow() = integrationFlow(PipelineChannels.KNOWN_DEVICE) {
        log<Classification.Identified>(LoggingHandler.Level.INFO, "pipeline.known") { message ->
            val identified = message.payload
            // deviceKey.id already leads with the model, so it is not repeated here.
            "known ${identified.deviceKey.id} parsed by ${identified.parseRuleSetId}: " +
                "${identified.envelope.payload}"
        }
        nullChannel()
    }

    // TODO recommendation engine: track devices by frequency and proximity and
    // recommend the worthwhile ones for promotion.
    @Bean
    fun unknownDeviceStubFlow() = integrationFlow(PipelineChannels.UNKNOWN_DEVICE) {
        log<Classification>(LoggingHandler.Level.DEBUG, "pipeline.unknown") { message ->
            when (val classification = message.payload) {
                // Model recognised, but the device has not been promoted to known.
                is Classification.Identified ->
                    "unpromoted ${classification.deviceKey.id} [${classification.parseRuleSetId}] " +
                        "from ${classification.envelope.origin}: ${classification.envelope.payload}"
                is Classification.Unidentified ->
                    "unidentified ${classification.deviceKey?.id ?: "device"} " +
                        "from ${classification.envelope.origin} (${classification.reason}): " +
                        "${classification.envelope.payload}"
            }
        }
        nullChannel()
    }

    @Bean
    fun ingestErrorFlow() = integrationFlow(PipelineChannels.INGEST_ERRORS) {
        log(LoggingHandler.Level.WARN, "pipeline.errors")
        nullChannel()
    }
}
