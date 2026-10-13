/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.store

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

/**
 * Up when the store answers, down when it cannot be reached. Also reports what has been
 * written since startup, so failing writes show even while the server answers.
 */
class StoreHealthIndicator(private val service: StoreService) : HealthIndicator {

    override fun health(): Health {
        val stats = service.stats()
        val builder = try {
            Health.up().withDetail("store", service.describe())
        } catch (e: Exception) {
            Health.down(e)
        }
        return builder
            .withDetail("readings", stats.readings)
            .withDetail("values", stats.values)
            .withDetail("failures", stats.failures)
            .apply { stats.lastStoredAt?.let { withDetail("lastStoredAt", it.toString()) } }
            .build()
    }
}
