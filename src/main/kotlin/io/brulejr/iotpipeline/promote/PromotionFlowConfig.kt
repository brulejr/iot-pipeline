/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote

import io.brulejr.iotpipeline.classify.Classification
import io.brulejr.iotpipeline.pipeline.PipelineChannels
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.handler.LoggingHandler

/**
 * The gate: stage 3 of the design, the branch between a known device and an unknown one.
 *
 * A reading continues to normal processing only if its device has been promoted.
 * Everything else goes to the recommendation engine, which is what decides whether a
 * device is worth proposing for promotion in the first place.
 */
@Configuration
class PromotionFlowConfig {

    @Bean
    fun promotionGateFlow(promotions: PromotionPort) = integrationFlow(PipelineChannels.CLASSIFIED) {
        // Declared as explicit mappings rather than by returning a channel name, so the
        // three outcomes appear as routes in /mgmt/integrationgraph instead of being
        // decided invisibly inside a lambda.
        route<Classification, Decision>({ classification ->
            val promoted = classification.deviceKey?.let { promotions.find(it) } != null
            when {
                !promoted -> Decision.UNPROMOTED
                // Approved, but nobody has said how to read this model's payload, so the
                // reading is not ready for normal processing however approved it is.
                !classification.model.recognised -> Decision.PROMOTED_BUT_UNREADABLE
                else -> Decision.PROMOTED
            }
        }) {
            channelMapping(Decision.PROMOTED, PipelineChannels.PROMOTED)
            channelMapping(Decision.PROMOTED_BUT_UNREADABLE, PipelineChannels.PROMOTION_GAPS)
            channelMapping(Decision.UNPROMOTED, PipelineChannels.UNKNOWN_DEVICE)
        }
    }

    /** What the gate decided, so the routing reads as a decision rather than a string. */
    enum class Decision { PROMOTED, PROMOTED_BUT_UNREADABLE, UNPROMOTED }

    /** Attaches the promotion, which later stages need alongside the model. */
    @Bean
    fun promotedReadingFlow(promotions: PromotionPort) = integrationFlow(PipelineChannels.PROMOTED) {
        transform<Classification> { classification ->
            PromotedReading(classification, promotions.find(classification.deviceKey!!)!!)
        }
        log<PromotedReading>(LoggingHandler.Level.DEBUG, "pipeline.promote") { message ->
            val reading = message.payload
            "promoted ${reading.device.name} (${reading.device.area}) " +
                "as ${reading.classification.deviceKey?.id}"
        }
        channel(PipelineChannels.KNOWN_DEVICE)
    }

    /**
     * A promoted device whose model has no sensor mappings. Someone approved the device
     * but nobody said how to read it, which is a gap worth seeing rather than a reading
     * worth dropping silently.
     */
    @Bean
    fun promotionGapFlow() = integrationFlow(PipelineChannels.PROMOTION_GAPS) {
        log<Classification>(LoggingHandler.Level.WARN, "pipeline.promote") { message ->
            val classification = message.payload
            "device ${classification.deviceKey?.id} is promoted but its model " +
                "${classification.model.name ?: "unnamed"} has no sensor mappings, so its " +
                "readings cannot be parsed; curate the model"
        }
        nullChannel()
    }
}
