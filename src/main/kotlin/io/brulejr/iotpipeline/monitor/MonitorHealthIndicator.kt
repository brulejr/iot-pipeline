/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.monitor

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

/**
 * Always up: the monitor only watches. Reports how many streams are open and whether any
 * are losing events because they cannot keep up.
 */
class MonitorHealthIndicator(private val service: MonitorService) : HealthIndicator {

    override fun health(): Health {
        val stats = service.stats()
        return Health.up()
            .withDetail("streams", stats.streams)
            .withDetail("observed", stats.observed)
            .withDetail("delivered", stats.delivered)
            .withDetail("dropped", stats.dropped)
            .withDetail("heardDevices", stats.heardDevices)
            .build()
    }
}
