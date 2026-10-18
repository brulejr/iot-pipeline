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
import io.brulejr.iotpipeline.pipeline.RegistryType
import io.brulejr.iotpipeline.pipeline.DatabaseMigrator
import io.brulejr.iotpipeline.promote.postgres.PostgresPromotionRegistry
import io.brulejr.iotpipeline.promote.seed.PromotionSeeder
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.handler.LoggingHandler
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.MessageBuilder
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper

/**
 * Stage 3: the promotion gate, the approvals it applies, and the seed file that restores
 * them at startup.
 */
@Configuration
class PromotionConfig {

    /**
     * PostgreSQL is reached only when chosen, so a context using the in-memory registry
     * never touches it.
     */
    @Bean
    fun promotionRegistry(
        datafill: PromotionDatafill,
        migrator: ObjectProvider<DatabaseMigrator>,
        jdbc: ObjectProvider<JdbcClient>,
        transactionManager: ObjectProvider<PlatformTransactionManager>,
    ): PromotionPort = when (datafill.registry) {
        RegistryType.POSTGRES -> {
            migrator.getObject().migrate()
            PostgresPromotionRegistry(jdbc.getObject(), TransactionTemplate(transactionManager.getObject()))
        }
        RegistryType.MEMORY -> InMemoryPromotionRegistry()
    }

    @Bean
    fun promotionService(
        registry: PromotionPort,
        @Qualifier(PipelineChannels.DEMOTED) demoted: MessageChannel,
    ) = PromotionService(registry) { deviceKey -> demoted.send(MessageBuilder.withPayload(deviceKey).build()) }

    @Bean
    fun promotionHealthIndicator(service: PromotionService) = PromotionHealthIndicator(service)

    @Bean
    fun promotionInfoContributor(datafill: PromotionDatafill) = PromotionInfoContributor(datafill)

    @Bean
    fun promotionSeeder(
        service: PromotionService,
        datafill: PromotionDatafill,
        jsonMapper: JsonMapper,
        resourceLoader: ResourceLoader,
    ) = PromotionSeeder(service, datafill.seed, jsonMapper, resourceLoader)

    @Bean
    fun promotionSeedRunner(seeder: PromotionSeeder) = ApplicationRunner { seeder.seed() }

    @Bean
    fun promotionGateFlow(service: PromotionService) = integrationFlow(PipelineChannels.CLASSIFIED) {
        // Declared as explicit mappings rather than by returning a channel name, so the
        // three outcomes appear as routes in /mgmt/integrationgraph instead of being
        // decided invisibly inside a lambda.
        route<Classification, PromotionDecision>({ service.decide(it) }) {
            channelMapping(PromotionDecision.PROMOTED, PipelineChannels.PROMOTED)
            channelMapping(PromotionDecision.PROMOTED_BUT_UNREADABLE, PipelineChannels.PROMOTION_GAPS)
            channelMapping(PromotionDecision.UNPROMOTED, PipelineChannels.UNKNOWN_DEVICE)
        }
    }

    /** Attaches the promotion, which later stages need alongside the model. */
    @Bean
    fun promotedReadingFlow(service: PromotionService) = integrationFlow(PipelineChannels.PROMOTED) {
        transform<Classification> { service.promoted(it) }
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
