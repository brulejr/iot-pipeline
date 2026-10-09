/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote

import io.brulejr.iotpipeline.promote.mongo.MongoPromotionRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoOperations

/**
 * Chooses where promotions live. The two conditions are mutually exclusive, so exactly
 * one bean is defined rather than relying on `@ConditionalOnMissingBean` ordering
 * between beans declared in application code.
 */
@Configuration
class PromotionRegistryConfig {

    @Bean
    @ConditionalOnProperty("pipeline.promotion-registry.type", havingValue = "mongo", matchIfMissing = true)
    fun mongoPromotionRegistry(mongo: MongoOperations): PromotionPort = MongoPromotionRegistry(mongo)

    /** Holds promotions for the lifetime of the process; needs no database. */
    @Bean
    @ConditionalOnProperty("pipeline.promotion-registry.type", havingValue = "memory")
    fun inMemoryPromotionRegistry(): PromotionPort = InMemoryPromotionRegistry()
}
