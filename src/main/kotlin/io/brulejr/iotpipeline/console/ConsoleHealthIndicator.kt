/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.console

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

/**
 * Down when any pipeline stage is, naming which: the console's view of the pipeline as
 * a whole, the same one its dashboard shows.
 */
class ConsoleHealthIndicator(private val service: ConsoleService) : HealthIndicator {

    override fun health(): Health {
        val stages = service.health()
        val down = stages.filter { it.status == "DOWN" || it.status == "OUT_OF_SERVICE" }.map { it.stage }
        val builder = if (down.isEmpty()) Health.up() else Health.down().withDetail("down", down)
        return builder.withDetail("stages", stages.associate { it.stage to it.status }).build()
    }
}
