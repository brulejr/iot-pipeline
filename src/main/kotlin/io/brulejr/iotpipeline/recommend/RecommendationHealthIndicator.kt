/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.recommend

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

/**
 * Up when the sighting registry answers. Reports how many unpromoted devices were heard
 * in the window, how many of them are recommended, and what has been recorded since
 * startup.
 */
class RecommendationHealthIndicator(private val service: RecommendationService) : HealthIndicator {

    override fun health(): Health = try {
        val recommendations = service.recommendations()
        val stats = service.stats()
        Health.up()
            .withDetail("candidates", recommendations.size)
            .withDetail("recommended", recommendations.count { it.recommended })
            .withDetail("recorded", stats.recorded)
            .withDetail("unidentified", stats.unidentified)
            .build()
    } catch (e: Exception) {
        Health.down(e).build()
    }
}
