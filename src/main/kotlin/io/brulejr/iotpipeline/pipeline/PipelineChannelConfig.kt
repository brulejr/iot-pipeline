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

@Configuration
class PipelineChannelConfig {

    @Bean(PipelineChannels.INGEST)
    fun ingestChannel(): MessageChannel = DirectChannel()

    @Bean(PipelineChannels.FINGERPRINTED)
    fun fingerprintedChannel(): MessageChannel = DirectChannel()

    @Bean(PipelineChannels.DEDUPED)
    fun dedupedChannel(): MessageChannel = DirectChannel()

    @Bean(PipelineChannels.CLASSIFIED)
    fun classifiedChannel(): MessageChannel = DirectChannel()

    @Bean(PipelineChannels.PROMOTED)
    fun promotedChannel(): MessageChannel = DirectChannel()

    @Bean(PipelineChannels.PROMOTION_GAPS)
    fun promotionGapChannel(): MessageChannel = DirectChannel()

    @Bean(PipelineChannels.DUPLICATES)
    fun duplicateChannel(): MessageChannel = DirectChannel()

    @Bean(PipelineChannels.KNOWN_DEVICE)
    fun knownDeviceChannel(): MessageChannel = DirectChannel()

    @Bean(PipelineChannels.UNKNOWN_DEVICE)
    fun unknownDeviceChannel(): MessageChannel = DirectChannel()

    @Bean(PipelineChannels.INGEST_ERRORS)
    fun ingestErrorChannel(): MessageChannel = DirectChannel()
}
