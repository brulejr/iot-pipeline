/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.recommend

import io.brulejr.iotpipeline.classify.Classification
import io.brulejr.iotpipeline.classify.ModelRegistryPort
import io.brulejr.iotpipeline.pipeline.PipelineChannels
import io.brulejr.iotpipeline.promote.PromotionPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.handler.LoggingHandler
import java.time.Clock

/**
 * Stage 4: notes every reading from an unpromoted device, so the devices worth promoting
 * can be ranked by how persistently and how nearby they are heard.
 *
 * The antenna hears every 433 MHz transmitter in range, so most of what arrives here is
 * a neighbour's or a passing car's. Nothing is promoted automatically; this only
 * suggests, and a person decides.
 */
@Configuration
class RecommendationFlowConfig {

    @Bean
    fun recommendationScorer(datafill: RecommendationDatafill) = RecommendationScorer(datafill)

    @Bean
    fun recommendationService(
        sightings: SightingRegistryPort,
        models: ModelRegistryPort,
        promotions: PromotionPort,
        scorer: RecommendationScorer,
        datafill: RecommendationDatafill,
    ) = RecommendationService(sightings, models, promotions, scorer, datafill, Clock.systemUTC())

    @Bean
    fun recommendationFlow(sightings: SightingRegistryPort, datafill: RecommendationDatafill) =
        integrationFlow(PipelineChannels.UNKNOWN_DEVICE) {
            log<Classification>(LoggingHandler.Level.DEBUG, "pipeline.unknown") { message ->
                val classification = message.payload
                "awaiting promotion: ${classification.deviceKey?.id ?: "unidentified device"} " +
                    "from ${classification.reading.envelope.origin}"
            }
            handle<Classification> { classification, _ ->
                // A reading with no device identity cannot be counted towards anything.
                val deviceKey = classification.deviceKey
                if (datafill.enabled && deviceKey != null) {
                    val payload = classification.reading.envelope.payload
                    sightings.record(
                        Sighting(
                            deviceKey = deviceKey,
                            modelName = classification.model.name,
                            modelFingerprint = classification.model.fingerprint,
                            receivedAt = classification.reading.envelope.receivedAt,
                            signal = payload.get(datafill.signalField)?.takeIf { it.isNumber }?.asDouble(),
                        ),
                    )
                }
                classification
            }
            nullChannel()
        }
}
