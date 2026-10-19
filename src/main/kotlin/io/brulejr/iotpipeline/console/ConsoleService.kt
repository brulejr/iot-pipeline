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
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.health.contributor.HealthIndicator

/**
 * Gathers what every stage reports into one overview of the pipeline. Reads only: the
 * console changes data through each feature's own service.
 *
 * @param ingest absent when MQTT ingestion is switched off.
 * @param healthIndicators looked up on each call rather than injected, since they
 *   include features that depend on this one's neighbours.
 */
class ConsoleService(
    private val ingest: ObjectProvider<MqttIngestionService>,
    private val dedupe: DedupeService,
    private val classification: ClassificationService,
    private val promotions: PromotionService,
    private val recommendations: RecommendationService,
    private val parse: ParseService,
    private val store: StoreService,
    private val transform: TransformService,
    private val publish: PublishService,
    private val monitor: MonitorService,
    private val healthIndicators: () -> Map<String, HealthIndicator>,
) {

    fun overview(): ConsoleOverview {
        val models = classification.models()
        val candidates = recommendations.recommendations()
        return ConsoleOverview(
            ingest = ingest.ifAvailable?.status(),
            dedupe = dedupe.stats(),
            classification = classification.stats(),
            promotion = promotions.stats(),
            parse = parse.stats(),
            store = store.stats(),
            transform = transform.stats(),
            publish = publish.stats(),
            monitor = monitor.stats(),
            models = models.size,
            curatedModels = models.count { it.recognised },
            promotedDevices = promotions.all().size,
            recommended = candidates.count { it.recommended },
            candidates = candidates.size,
            health = health(),
        )
    }

    /**
     * Each stage's health, in the order a reading meets them. An indicator that throws is
     * reported down rather than failing the dashboard.
     */
    fun health(): List<StageHealth> {
        val indicators = healthIndicators()
        return STAGES.mapNotNull { stage ->
            val indicator = indicators["${stage}HealthIndicator"] ?: return@mapNotNull null
            try {
                val health = indicator.health()
                StageHealth(stage, health?.status?.code ?: "UNKNOWN", health?.details.orEmpty())
            } catch (e: Exception) {
                StageHealth(stage, "DOWN", mapOf("error" to (e.message ?: e.javaClass.simpleName)))
            }
        }
    }

    private companion object {
        val STAGES = listOf(
            "mqttIngestion", "fingerprint", "dedupe", "classification", "promotion", "recommendation",
            "parse", "store", "transform", "publish", "monitor",
        )
    }
}
