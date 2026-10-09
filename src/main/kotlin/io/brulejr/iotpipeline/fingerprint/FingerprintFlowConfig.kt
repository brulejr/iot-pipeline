/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.fingerprint

import io.brulejr.iotpipeline.ingest.SensorEnvelope
import io.brulejr.iotpipeline.pipeline.PipelineChannels
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.handler.LoggingHandler
import tools.jackson.databind.json.JsonMapper

/**
 * Fingerprints every ingested reading before anything else looks at it: dedupe needs
 * the event and device hashes, and classification needs the model hash.
 */
@Configuration
class FingerprintFlowConfig {

    @Bean
    fun fingerprintService(datafill: FingerprintDatafill, jsonMapper: JsonMapper) =
        FingerprintService(datafill, jsonMapper)

    @Bean
    fun fingerprintFlow(fingerprintService: FingerprintService) =
        integrationFlow(PipelineChannels.INGEST) {
            transform<SensorEnvelope> { envelope ->
                FingerprintedReading(envelope, fingerprintService.fingerprint(envelope))
            }
            log<FingerprintedReading>(LoggingHandler.Level.DEBUG, "pipeline.fingerprint") { message ->
                val fingerprint = message.payload.fingerprint
                // Truncated: enough to correlate lines by eye, the full hashes are on
                // the model record.
                "event=${fingerprint.event.take(12)} device=${fingerprint.device.take(12)} " +
                    "model=${fingerprint.model.take(12)}"
            }
            channel(PipelineChannels.FINGERPRINTED)
        }
}
