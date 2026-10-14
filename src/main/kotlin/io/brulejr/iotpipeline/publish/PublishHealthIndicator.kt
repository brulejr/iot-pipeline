/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.publish

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

/**
 * Down while publishing is failing and messages are held, up otherwise. Up does not
 * prove the broker is there until something has been published: the connection is made
 * on demand, by the first message.
 */
class PublishHealthIndicator(
    private val service: PublishService,
    private val datafill: PublishDatafill,
) : HealthIndicator {

    override fun health(): Health {
        if (!datafill.enabled) return Health.up().withDetail("enabled", false).build()
        val stats = service.stats()
        val builder = if (stats.circuitOpen) Health.down() else Health.up()
        return builder
            .withDetail("broker", datafill.mqtt.url)
            .withDetail("published", stats.published)
            .withDetail("pending", stats.pending)
            .withDetail("superseded", stats.superseded)
            .withDetail("failures", stats.failures)
            .apply { stats.lastPublishedAt?.let { withDetail("lastPublishedAt", it.toString()) } }
            .apply { stats.lastFailure?.let { withDetail("lastFailure", it) } }
            .apply { stats.nextAttemptAt?.let { withDetail("nextAttemptAt", it.toString()) } }
            .build()
    }
}
