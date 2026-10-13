/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.transform

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

/**
 * Always up: transformation is pure computation and publication is not built yet.
 * Reports how much has been produced.
 */
class TransformHealthIndicator(private val service: TransformService) : HealthIndicator {

    override fun health(): Health {
        val stats = service.stats()
        return Health.up()
            .withDetail("readings", stats.readings)
            .withDetail("messages", stats.messages)
            .withDetail("topics", stats.topics)
            .build()
    }
}
