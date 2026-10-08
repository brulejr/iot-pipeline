/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify

import io.brulejr.iotpipeline.ingest.SensorEnvelope
import io.brulejr.iotpipeline.pipeline.PipelineChannels
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.integrationFlow

/**
 * Classifies every envelope on [PipelineChannels.INGEST] and branches on the result
 * into the known- and unknown-device channels.
 */
@Configuration
class ClassificationFlowConfig {

    @Bean
    @ConditionalOnMissingBean(ClassificationPort::class)
    fun noRulesClassifier(): ClassificationPort = NoRulesClassifier()

    @Bean
    fun classificationFlow(classifier: ClassificationPort) = integrationFlow(PipelineChannels.INGEST) {
        transform<SensorEnvelope> { classifier.classify(it) }
        route<Classification> {
            when (it) {
                is Classification.Known -> PipelineChannels.KNOWN_DEVICE
                is Classification.Unknown -> PipelineChannels.UNKNOWN_DEVICE
            }
        }
    }
}
