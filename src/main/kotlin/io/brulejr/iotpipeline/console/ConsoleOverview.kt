/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.console

import io.brulejr.iotpipeline.classify.ClassificationStats
import io.brulejr.iotpipeline.dedupe.DedupeStats
import io.brulejr.iotpipeline.ingest.mqtt.MqttIngestionStatus
import io.brulejr.iotpipeline.monitor.MonitorStats
import io.brulejr.iotpipeline.parse.ParseStats
import io.brulejr.iotpipeline.promote.PromotionStats
import io.brulejr.iotpipeline.publish.PublishStats
import io.brulejr.iotpipeline.store.StoreStats
import io.brulejr.iotpipeline.transform.TransformStats

/**
 * Everything the dashboard shows, gathered at once.
 *
 * @property ingest null when MQTT ingestion is switched off.
 * @property curatedModels models with sensor mappings, of [models].
 * @property recommended unpromoted devices suggested for promotion, of [candidates] heard.
 * @property health every stage's health, in pipeline order.
 */
data class ConsoleOverview(
    val ingest: MqttIngestionStatus?,
    val dedupe: DedupeStats,
    val classification: ClassificationStats,
    val promotion: PromotionStats,
    val parse: ParseStats,
    val store: StoreStats,
    val transform: TransformStats,
    val publish: PublishStats,
    val monitor: MonitorStats,
    val models: Int,
    val curatedModels: Int,
    val promotedDevices: Int,
    val recommended: Int,
    val candidates: Int,
    val health: List<StageHealth>,
)
