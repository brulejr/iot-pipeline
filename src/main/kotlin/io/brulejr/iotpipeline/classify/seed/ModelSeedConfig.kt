/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify.seed

import io.brulejr.iotpipeline.classify.ModelRegistryPort
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import tools.jackson.databind.json.JsonMapper

/**
 * Seeds the registry once the context is ready, before any reading is classified.
 */
@Configuration
class ModelSeedConfig {

    @Bean
    fun modelSeeder(
        registry: ModelRegistryPort,
        datafill: ModelSeedDatafill,
        jsonMapper: JsonMapper,
        resourceLoader: ResourceLoader,
    ) = ModelSeeder(registry, datafill, jsonMapper, resourceLoader)

    @Bean
    fun modelSeedRunner(seeder: ModelSeeder) = ApplicationRunner { seeder.seed() }
}
