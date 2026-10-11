/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote.seed

import io.brulejr.iotpipeline.promote.PromotionPort
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import tools.jackson.databind.json.JsonMapper

/**
 * Restores approvals once the context is ready, before any reading reaches the gate.
 */
@Configuration
class PromotionSeedConfig {

    @Bean
    fun promotionSeeder(
        promotions: PromotionPort,
        datafill: PromotionSeedDatafill,
        jsonMapper: JsonMapper,
        resourceLoader: ResourceLoader,
    ) = PromotionSeeder(promotions, datafill, jsonMapper, resourceLoader)

    @Bean
    fun promotionSeedRunner(seeder: PromotionSeeder) = ApplicationRunner { seeder.seed() }
}
