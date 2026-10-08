/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.pipeline

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

    // TODO storage stage: write known-device readings to InfluxDB.
    @Bean
    fun knownDeviceStubFlow() = integrationFlow(PipelineChannels.KNOWN_DEVICE) {
        log<Any>(LoggingHandler.Level.INFO, "pipeline.known") { it.payload }
        nullChannel()
    }

    // TODO recommendation engine: track unknown devices by frequency and proximity.
    @Bean
    fun unknownDeviceStubFlow() = integrationFlow(PipelineChannels.UNKNOWN_DEVICE) {
        log<Any>(LoggingHandler.Level.DEBUG, "pipeline.unknown") { it.payload }
        nullChannel()
    }

    @Bean
    fun ingestErrorFlow() = integrationFlow(PipelineChannels.INGEST_ERRORS) {
        log(LoggingHandler.Level.WARN, "pipeline.errors")
        nullChannel()
    }
}
