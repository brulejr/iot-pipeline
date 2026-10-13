/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.dedupe

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

/**
 * Always up: dedupe is an in-memory cache with nothing to fail. Reports how much it has
 * discarded, which is how a window set too long or too short shows itself.
 */
class DedupeHealthIndicator(private val service: DedupeService) : HealthIndicator {

    override fun health(): Health {
        val stats = service.stats()
        return Health.up()
            .withDetail("unique", stats.unique)
            .withDetail("duplicates", stats.duplicates)
            .withDetail("rememberedDevices", stats.rememberedDevices)
            .build()
    }
}
