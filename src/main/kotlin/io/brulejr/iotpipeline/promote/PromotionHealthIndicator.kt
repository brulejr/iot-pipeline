/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

/**
 * Up when the promotion registry answers. Reports how many devices are approved and how
 * the gate has decided since startup.
 */
class PromotionHealthIndicator(private val service: PromotionService) : HealthIndicator {

    override fun health(): Health = try {
        val promotedDevices = service.all().size
        val stats = service.stats()
        Health.up()
            .withDetail("promotedDevices", promotedDevices)
            .withDetail("promoted", stats.promoted)
            .withDetail("promotedButUnreadable", stats.promotedButUnreadable)
            .withDetail("unpromoted", stats.unpromoted)
            .build()
    } catch (e: Exception) {
        Health.down(e).build()
    }
}
