/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.store

import com.influxdb.v3.client.InfluxDBClient
import io.brulejr.iotpipeline.parse.ParsedReading
import io.brulejr.iotpipeline.pipeline.PipelineChannels
import io.brulejr.iotpipeline.store.influx.InfluxDatabaseInitializer
import io.brulejr.iotpipeline.store.influx.InfluxReadingStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.handler.LoggingHandler
import java.time.Clock

/**
 * Stage 5: parsed readings are written to InfluxDB, the design's neutral handoff
 * boundary. With `pipeline.store.enabled` false they are discarded instead, and nothing
 * here touches InfluxDB.
 */
@Configuration
class StoreConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty("pipeline.store.enabled", havingValue = "true", matchIfMissing = true)
    fun influxDBClient(datafill: StoreDatafill): InfluxDBClient =
        InfluxDBClient.getInstance(datafill.url, datafill.token.toCharArray(), datafill.database)

    @Bean
    @ConditionalOnProperty("pipeline.store.enabled", havingValue = "true", matchIfMissing = true)
    fun influxDatabaseInitializer(datafill: StoreDatafill) = InfluxDatabaseInitializer(datafill)

    /**
     * Depends on the initializer so the database exists, with its retention period,
     * before this bean can be used to write anything.
     */
    @Bean
    @ConditionalOnProperty("pipeline.store.enabled", havingValue = "true", matchIfMissing = true)
    fun influxReadingStore(
        client: InfluxDBClient,
        datafill: StoreDatafill,
        @Suppress("UNUSED_PARAMETER") initializer: InfluxDatabaseInitializer,
    ): ReadingStorePort = InfluxReadingStore(client, datafill)

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
