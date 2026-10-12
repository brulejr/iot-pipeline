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
 * Logs failures raised while a reading travels the pipeline.
 *
 * The MQTT adapter routes an exception from any stage downstream of it here, since every
 * channel is direct and the failure unwinds back to the adapter. Logged at WARN rather
 * than dropped, so a stage that starts failing is visible.
 */
@Configuration
class IngestErrorFlowConfig {

    @Bean
    fun ingestErrorFlow() = integrationFlow(PipelineChannels.INGEST_ERRORS) {
        log(LoggingHandler.Level.WARN, "pipeline.errors")
        nullChannel()
    }
}
