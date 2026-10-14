/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.publish

import io.brulejr.iotpipeline.pipeline.PipelineChannels
import io.brulejr.iotpipeline.publish.mqtt.MqttPublisher
import io.brulejr.iotpipeline.transform.OutboundMessage
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.integrationFlow
import org.springframework.scheduling.TaskScheduler
import java.time.Clock
import java.time.Duration

/**
 * Stage 7: publishes each outbound message to the consumer's broker, holding what fails
 * until the broker is back.
 */
@Configuration
class PublishConfig {

    @Bean(destroyMethod = "close")
    fun mqttPublisher(datafill: PublishDatafill) = MqttPublisher(datafill.mqtt)

    @Bean
    fun publishService(publisher: PublisherPort, datafill: PublishDatafill) =
        PublishService(publisher, datafill, Clock.systemUTC())

    @Bean
    fun publishHealthIndicator(service: PublishService, datafill: PublishDatafill) =
        PublishHealthIndicator(service, datafill)

    @Bean
    fun publishInfoContributor(datafill: PublishDatafill) = PublishInfoContributor(datafill)

    /**
     * Checks every second whether held messages are due another try. The backoff itself
     * is the service's, so this only needs to be finer than the shortest backoff.
     */
    @Bean
    fun publishRetryRunner(scheduler: TaskScheduler, service: PublishService) = ApplicationRunner {
        scheduler.scheduleWithFixedDelay({ service.retryIfDue() }, RETRY_TICK)
    }

    @Bean
    fun publishFlow(service: PublishService) = integrationFlow(PipelineChannels.OUTBOUND) {
        handle<OutboundMessage> { message, _ -> message.also { service.publish(it) } }
        nullChannel()
    }

    private companion object {
        val RETRY_TICK: Duration = Duration.ofSeconds(1)
    }
}
