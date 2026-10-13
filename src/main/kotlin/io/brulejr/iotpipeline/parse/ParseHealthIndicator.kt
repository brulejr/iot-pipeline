/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.parse

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

/**
 * Always up: parsing is pure computation with nothing to fail. Reports how many readings
 * came out incomplete, which points at a model whose curation no longer matches what
 * its devices send.
 */
class ParseHealthIndicator(private val service: ParseService) : HealthIndicator {

    override fun health(): Health {
        val stats = service.stats()
        return Health.up()
            .withDetail("parsed", stats.parsed)
            .withDetail("incomplete", stats.incomplete)
            .withDetail("dropped", stats.dropped)
            .build()
    }
}
