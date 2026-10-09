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
import org.springframework.integration.handler.LoggingHandler

/**
 * Identifies the device model of every envelope on [PipelineChannels.INGEST].
 */
@Configuration
class ClassificationFlowConfig {

    @Bean
    @ConditionalOnMissingBean(ClassificationPort::class)
    fun ruleBasedClassifier(): ClassificationPort = RuleBasedClassifier(CURATED_CLASSIFICATION_RULES)

    @Bean
    fun classificationFlow(classifier: ClassificationPort) = integrationFlow(PipelineChannels.INGEST) {
        transform<SensorEnvelope> { classifier.classify(it) }
        // Which model a reading was recognised as, and the rule set that will parse it
        // once the device is promoted. The raw payload is on the pipeline.ingest line
        // for the same reading, so it is not repeated here.
        log<Classification>(LoggingHandler.Level.DEBUG, "pipeline.classify") { message ->
            when (val classification = message.payload) {
                is Classification.Identified ->
                    "identified ${classification.deviceKey.id} as model ${classification.model}, " +
                        "parsed by ${classification.parseRuleSetId}"
                is Classification.Unidentified ->
                    "unidentified ${classification.deviceKey?.id ?: "device"}: ${classification.reason}"
            }
        }
        // TODO stage 3: branch on whether this device has been promoted to known,
        // sending promoted devices to PipelineChannels.KNOWN_DEVICE to be parsed with
        // their model's rule set. Promotion is a manual step that does not exist yet,
        // so no device is known and every reading - model recognised or not - goes to
        // the recommendation engine, which works on raw payloads.
        channel(PipelineChannels.UNKNOWN_DEVICE)
    }
}
