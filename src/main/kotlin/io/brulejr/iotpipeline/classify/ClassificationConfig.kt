/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify

import io.brulejr.iotpipeline.classify.mongo.MongoModelRegistry
import io.brulejr.iotpipeline.classify.seed.ModelSeeder
import io.brulejr.iotpipeline.fingerprint.FingerprintedReading
import io.brulejr.iotpipeline.pipeline.PipelineChannels
import io.brulejr.iotpipeline.pipeline.RegistryType
import io.brulejr.iotpipeline.pipeline.requireResolvedMongoUri
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.mongodb.autoconfigure.MongoProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import org.springframework.data.mongodb.core.MongoOperations
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.handler.LoggingHandler
import tools.jackson.databind.json.JsonMapper

/**
 * Stage 2: identifies the model of every deduplicated reading, curates models, and
 * restores curation from the seed file at startup.
 */
@Configuration
class ClassificationConfig {

    /**
     * MongoDB is looked up only when chosen, so a context using the in-memory registry
     * never touches it.
     */
    @Bean
    fun modelRegistry(
        datafill: ClassificationDatafill,
        mongo: ObjectProvider<MongoOperations>,
        mongoProperties: ObjectProvider<MongoProperties>,
    ): ModelRegistryPort = when (datafill.registry) {
        RegistryType.MONGO -> {
            requireResolvedMongoUri(mongoProperties.getObject())
            MongoModelRegistry(mongo.getObject())
        }
        RegistryType.MEMORY -> InMemoryModelRegistry()
    }

    @Bean
    fun classificationService(registry: ModelRegistryPort, jsonMapper: JsonMapper) =
        ClassificationService(registry, jsonMapper)

    @Bean
    fun classificationHealthIndicator(service: ClassificationService, datafill: ClassificationDatafill) =
        ClassificationHealthIndicator(service, datafill)

    @Bean
    fun classificationInfoContributor(datafill: ClassificationDatafill) = ClassificationInfoContributor(datafill)

    @Bean
    fun modelSeeder(
        service: ClassificationService,
        datafill: ClassificationDatafill,
        jsonMapper: JsonMapper,
        resourceLoader: ResourceLoader,
    ) = ModelSeeder(service, datafill.seed, jsonMapper, resourceLoader)

    @Bean
    fun modelSeedRunner(seeder: ModelSeeder) = ApplicationRunner { seeder.seed() }

    @Bean
    fun classificationFlow(service: ClassificationService) = integrationFlow(PipelineChannels.DEDUPED) {
        transform<FingerprintedReading> { service.classify(it) }
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
        // The promotion gate decides where this goes.
        channel(PipelineChannels.CLASSIFIED)
    }
}
