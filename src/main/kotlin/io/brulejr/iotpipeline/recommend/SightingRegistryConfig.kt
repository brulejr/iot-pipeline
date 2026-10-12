/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.recommend

import io.brulejr.iotpipeline.recommend.mongo.MongoSightingRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoOperations

/**
 * Chooses where sightings live. The conditions are mutually exclusive, so exactly one
 * bean is defined, as with the other registries.
 */
@Configuration
class SightingRegistryConfig {

    @Bean
    @ConditionalOnProperty("pipeline.sighting-registry.type", havingValue = "mongo", matchIfMissing = true)
    fun mongoSightingRegistry(mongo: MongoOperations): SightingRegistryPort = MongoSightingRegistry(mongo)

    @Bean
    @ConditionalOnProperty("pipeline.sighting-registry.type", havingValue = "memory")
    fun inMemorySightingRegistry(): SightingRegistryPort = InMemorySightingRegistry()
}
