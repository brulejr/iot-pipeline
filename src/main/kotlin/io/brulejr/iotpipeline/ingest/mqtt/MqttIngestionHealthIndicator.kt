/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.ingest.mqtt

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

/**
 * Up while subscribed to the broker, down once the connection has failed or been lost,
 * and unknown until the adapter has done either. Down rather than merely noted, since
 * nothing reaches the pipeline without it.
 */
class MqttIngestionHealthIndicator(
    private val service: MqttIngestionService,
    private val datafill: MqttIngestionDatafill,
) : HealthIndicator {

    override fun health(): Health {
        val status = service.status()
        val builder = when (status.connected) {
            true -> Health.up()
            false -> Health.down()
            null -> Health.unknown()
        }
        return builder
            .withDetail("broker", datafill.url)
            .withDetail("received", status.received)
            .withDetail("malformed", status.malformed)
            .apply { status.lastReceivedAt?.let { withDetail("lastReceivedAt", it.toString()) } }
            .apply { status.lastFailure?.let { withDetail("lastFailure", it) } }
            .build()
    }
}
