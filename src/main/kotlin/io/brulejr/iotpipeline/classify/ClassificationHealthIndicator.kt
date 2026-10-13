/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

/**
 * Up when the model registry answers. Reports how many models are known and curated,
 * and how readings have classified since startup.
 */
class ClassificationHealthIndicator(
    private val service: ClassificationService,
    private val datafill: ClassificationDatafill,
) : HealthIndicator {

    override fun health(): Health = try {
        val models = service.models()
        val stats = service.stats()
        Health.up()
            .withDetail("registry", datafill.registry)
            .withDetail("models", models.size)
            .withDetail("curated", models.count { it.recognised })
            .withDetail("classified", stats.classified)
            .withDetail("recognised", stats.recognised)
            .withDetail("unrecognised", stats.unrecognised)
            .build()
    } catch (e: Exception) {
        Health.down(e).withDetail("registry", datafill.registry).build()
    }
}
