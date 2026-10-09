/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.dedupe

import io.brulejr.iotpipeline.fingerprint.FingerprintedReading
import io.brulejr.iotpipeline.pipeline.PipelineChannels
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.handler.LoggingHandler

/**
 * Drops readings already seen from the same transmitter inside the dedupe window, so
 * later stages count one transmission once.
 */
@Configuration
class DedupeFlowConfig {

    @Bean
    fun dedupeService(datafill: DedupeDatafill) = DedupeService(datafill)

    @Bean
    fun dedupeFlow(dedupeService: DedupeService) = integrationFlow(PipelineChannels.FINGERPRINTED) {
        filter<FingerprintedReading>({ dedupeService.isUnique(it) }) {
            discardChannel(PipelineChannels.DUPLICATES)
        }
        channel(PipelineChannels.DEDUPED)
    }

    @Bean
    fun duplicateFlow() = integrationFlow(PipelineChannels.DUPLICATES) {
        log<FingerprintedReading>(LoggingHandler.Level.DEBUG, "pipeline.dedupe") { message ->
            val reading = message.payload
            "duplicate of device=${reading.fingerprint.device.take(12)} " +
                "from ${reading.envelope.origin}, discarded"
        }
        nullChannel()
    }
}
