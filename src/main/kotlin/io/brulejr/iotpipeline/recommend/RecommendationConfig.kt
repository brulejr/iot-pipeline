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
import io.brulejr.iotpipeline.classify.ClassificationService
import io.brulejr.iotpipeline.pipeline.PipelineChannels
import io.brulejr.iotpipeline.pipeline.RegistryType
import io.brulejr.iotpipeline.pipeline.requireResolvedMongoUri
import io.brulejr.iotpipeline.promote.PromotionService
import io.brulejr.iotpipeline.recommend.mongo.MongoSightingRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.mongodb.autoconfigure.MongoProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoOperations
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.handler.LoggingHandler
import java.time.Clock

/**
 * Stage 4: records every reading from an unpromoted device, so the devices worth
 * promoting can be ranked by how persistently and how nearby they are heard.
 */
@Configuration
class RecommendationConfig {

    /**
     * MongoDB is looked up only when chosen, so a context using the in-memory registry
     * never touches it.
     */
    @Bean
    fun sightingRegistry(
        datafill: RecommendationDatafill,
        mongo: ObjectProvider<MongoOperations>,
        mongoProperties: ObjectProvider<MongoProperties>,
    ): SightingRegistryPort = when (datafill.registry) {
        RegistryType.MONGO -> {
            requireResolvedMongoUri(mongoProperties.getObject())
            MongoSightingRegistry(mongo.getObject())
        }
        RegistryType.MEMORY -> InMemorySightingRegistry()
    }

    @Bean
    fun recommendationScorer(datafill: RecommendationDatafill) = RecommendationScorer(datafill)

    @Bean
    fun recommendationService(
        sightings: SightingRegistryPort,
        classification: ClassificationService,
        promotions: PromotionService,
        scorer: RecommendationScorer,
        datafill: RecommendationDatafill,
    ) = RecommendationService(sightings, classification, promotions, scorer, datafill, Clock.systemUTC())

    @Bean
    fun recommendationHealthIndicator(service: RecommendationService) = RecommendationHealthIndicator(service)

    @Bean
    fun recommendationInfoContributor(datafill: RecommendationDatafill) = RecommendationInfoContributor(datafill)

    @Bean
    fun recommendationFlow(service: RecommendationService) = integrationFlow(PipelineChannels.UNKNOWN_DEVICE) {
        log<Classification>(LoggingHandler.Level.DEBUG, "pipeline.unknown") { message ->
            val classification = message.payload
            "awaiting promotion: ${classification.deviceKey?.id ?: "unidentified device"} " +
                "from ${classification.reading.envelope.origin}"
        }
        handle<Classification> { classification, _ -> classification.also { service.record(it) } }
        nullChannel()
    }
}
