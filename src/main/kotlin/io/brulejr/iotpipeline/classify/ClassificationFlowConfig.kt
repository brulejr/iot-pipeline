/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify

import io.brulejr.iotpipeline.fingerprint.FingerprintedReading
import io.brulejr.iotpipeline.pipeline.PipelineChannels
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.handler.LoggingHandler

/**
 * Identifies the device model of every deduplicated reading.
 */
@Configuration
class ClassificationFlowConfig {

    @Bean
    @ConditionalOnMissingBean(ClassificationPort::class)
    fun fingerprintClassifier(registry: ModelRegistryPort): ClassificationPort =
        FingerprintClassifier(registry)

    @Bean
    fun classificationFlow(classifier: ClassificationPort) = integrationFlow(PipelineChannels.DEDUPED) {
        transform<FingerprintedReading> { classifier.classify(it) }
        // Which model a reading was matched to, and whether that model can be parsed.
        // The raw payload is on the pipeline.ingest line for the same reading.
        log<Classification>(LoggingHandler.Level.DEBUG, "pipeline.classify") { message ->
            val classification = message.payload
            val model = classification.model
            val name = model.name ?: "unnamed"
            val device = classification.deviceKey?.id ?: "unidentified device"
            when (classification) {
                is Classification.Recognised ->
                    "recognised $device as $name [${model.fingerprint.take(12)}], " +
                        "${model.sensors.size} sensor mapping(s)"
                is Classification.Unrecognised ->
                    "unrecognised $device, model $name [${model.fingerprint.take(12)}]: " +
                        classification.reason
            }
        }
        // The promotion gate decides where this goes; see PromotionFlowConfig.
        channel(PipelineChannels.CLASSIFIED)
    }
}
