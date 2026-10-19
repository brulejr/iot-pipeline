/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.monitor

import io.brulejr.iotpipeline.classify.Classification
import io.brulejr.iotpipeline.fingerprint.FingerprintDatafill
import io.brulejr.iotpipeline.pipeline.PipelineChannels
import io.brulejr.iotpipeline.promote.PromotionService
import io.brulejr.iotpipeline.recommend.RecommendationDatafill
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.integrationFlow

/**
 * The live monitor: a wire tap on classification hands it every reading, promoted or
 * not, which it remembers by device and streams to whoever is watching.
 */
@Configuration
class MonitorConfig {

    /**
     * Radio metadata is the set dedupe ignores, and the signal is the field recommendation
     * scores by, so the monitor sees readings the way the rest of the pipeline does.
     */
    @Bean
    fun monitorService(
        datafill: MonitorDatafill,
        promotions: PromotionService,
        fingerprint: FingerprintDatafill,
        recommendation: RecommendationDatafill,
    ) = MonitorService(datafill, promotions, fingerprint.excludedEventFields, recommendation.signalField)

    @Bean
    fun monitorHealthIndicator(service: MonitorService) = MonitorHealthIndicator(service)

    @Bean
    fun monitorInfoContributor(datafill: MonitorDatafill) = MonitorInfoContributor(datafill)

    /** Fed by the wire tap in classification, so it sees every reading the gate does. */
    @Bean
    fun monitorFlow(service: MonitorService) = integrationFlow(PipelineChannels.MONITOR) {
        handle<Classification> { classification, _ -> classification.also { service.observe(it) } }
        nullChannel()
    }
}
