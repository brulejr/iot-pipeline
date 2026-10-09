/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.pipeline

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.channel.DirectChannel
import org.springframework.messaging.MessageChannel

/**
 * Names of the channels that connect the pipeline stages.
 *
 * ```
 * [MQTT adapter] ─┐
 * [future REST]  ─┴─> ingest ──> fingerprinted ──> deduped ──> classification
 *                                       │                           │
 *                                  duplicates                       ├─> knownDevice   ──> (storage)
 *                                  (discarded)                      └─> unknownDevice ──> (recommendation engine)
 * ```
 *
 * Every ingestion adapter, whatever its transport, ends by sending a
 * [io.brulejr.iotpipeline.ingest.SensorEnvelope] to [INGEST].
 */
object PipelineChannels {
    const val INGEST = "ingestChannel"
    const val FINGERPRINTED = "fingerprintedChannel"
    const val DEDUPED = "dedupedChannel"
    const val DUPLICATES = "duplicateChannel"
    const val KNOWN_DEVICE = "knownDeviceChannel"
    const val UNKNOWN_DEVICE = "unknownDeviceChannel"
    const val INGEST_ERRORS = "ingestErrorChannel"
}

@Configuration
class PipelineChannelConfig {

    @Bean(PipelineChannels.INGEST)
    fun ingestChannel(): MessageChannel = DirectChannel()

    @Bean(PipelineChannels.FINGERPRINTED)
    fun fingerprintedChannel(): MessageChannel = DirectChannel()

    @Bean(PipelineChannels.DEDUPED)
    fun dedupedChannel(): MessageChannel = DirectChannel()

    @Bean(PipelineChannels.DUPLICATES)
    fun duplicateChannel(): MessageChannel = DirectChannel()

    @Bean(PipelineChannels.KNOWN_DEVICE)
    fun knownDeviceChannel(): MessageChannel = DirectChannel()

    @Bean(PipelineChannels.UNKNOWN_DEVICE)
    fun unknownDeviceChannel(): MessageChannel = DirectChannel()

    @Bean(PipelineChannels.INGEST_ERRORS)
    fun ingestErrorChannel(): MessageChannel = DirectChannel()
}
