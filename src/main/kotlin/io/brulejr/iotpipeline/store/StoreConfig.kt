/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.store

import io.brulejr.iotpipeline.parse.ParsedReading
import io.brulejr.iotpipeline.pipeline.DatabaseMigrator
import io.brulejr.iotpipeline.pipeline.PipelineChannels
import io.brulejr.iotpipeline.store.postgres.PostgresReadingStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.handler.LoggingHandler
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/**
 * Stage 5: parsed readings are written to PostgreSQL, the design's neutral handoff
 * boundary. With `pipeline.store.enabled` false they are discarded instead, and nothing
 * here touches the database.
 */
@Configuration
class StoreConfig {

    /**
     * Migrates the schema while the bean is created, so the table exists before the
     * first reading arrives and startup fails if PostgreSQL cannot be reached.
     */
    @Bean
    @ConditionalOnProperty("pipeline.store.enabled", havingValue = "true", matchIfMissing = true)
    fun postgresReadingStore(
        migrator: DatabaseMigrator,
        jdbc: NamedParameterJdbcOperations,
        transactionManager: PlatformTransactionManager,
        datafill: StoreDatafill,
    ): ReadingStorePort {
        migrator.migrate()
        return PostgresReadingStore(jdbc, TransactionTemplate(transactionManager), datafill, Clock.systemUTC())
    }

    @Bean
    @ConditionalOnProperty("pipeline.store.enabled", havingValue = "false")
    fun discardingReadingStore(): ReadingStorePort = DiscardingReadingStore()

    @Bean
    fun storeService(store: ReadingStorePort) = StoreService(store, Clock.systemUTC())

    @Bean
    fun storeHealthIndicator(service: StoreService) = StoreHealthIndicator(service)

    @Bean
    fun storeInfoContributor(datafill: StoreDatafill) = StoreInfoContributor(datafill)

    @Bean
    fun storeFlow(service: StoreService) = integrationFlow(PipelineChannels.PARSED) {
        handle<ParsedReading> { reading, _ -> reading.also { service.store(it) } }
        log<ParsedReading>(LoggingHandler.Level.DEBUG, "pipeline.store") { message ->
            val reading = message.payload
            "stored ${reading.values.size} value(s) for ${reading.device.deviceKey.id}"
        }
        // Onward to transformation, only once the reading is kept.
        channel(PipelineChannels.STORED)
    }
}
