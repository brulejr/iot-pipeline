/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.console

import io.brulejr.iotpipeline.classify.ClassificationService
import io.brulejr.iotpipeline.dedupe.DedupeService
import io.brulejr.iotpipeline.ingest.mqtt.MqttIngestionService
import io.brulejr.iotpipeline.monitor.MonitorService
import io.brulejr.iotpipeline.parse.ParseService
import io.brulejr.iotpipeline.promote.PromotionService
import io.brulejr.iotpipeline.publish.PublishService
import io.brulejr.iotpipeline.recommend.RecommendationService
import io.brulejr.iotpipeline.store.StoreService
import io.brulejr.iotpipeline.transform.TransformService
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The operator console under `/ui`: server-rendered pages over every feature's service,
 * for curating models, promoting devices and watching readings arrive. Its page
 * controllers live in each feature's `web` package; this declares what they share.
 */
@Configuration
class ConsoleConfig {

    @Bean
    fun consoleService(
        ingest: ObjectProvider<MqttIngestionService>,
        dedupe: DedupeService,
        classification: ClassificationService,
        promotions: PromotionService,
        recommendations: RecommendationService,
        parse: ParseService,
        store: StoreService,
        transform: TransformService,
        publish: PublishService,
        monitor: MonitorService,
        beans: ListableBeanFactory,
    ) = ConsoleService(
        ingest, dedupe, classification, promotions, recommendations, parse, store, transform, publish, monitor,
    ) { beans.getBeansOfType(HealthIndicator::class.java) }

    @Bean
    fun consoleHealthIndicator(service: ConsoleService) = ConsoleHealthIndicator(service)

    @Bean
    fun consoleInfoContributor(datafill: ConsoleDatafill) = ConsoleInfoContributor(datafill)
}
