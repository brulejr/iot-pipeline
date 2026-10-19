/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.console

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * The operator console, served under `/ui`.
 *
 * @property refreshInterval how often the dashboard and the heard-devices panel reload
 *   themselves.
 * @property recentReadings how many stored values a device's page lists.
 */
@ConfigurationProperties("pipeline.console")
data class ConsoleDatafill(
    val refreshInterval: Duration = Duration.ofSeconds(10),
    val recentReadings: Int = 100,
) {
    init {
        require(refreshInterval >= Duration.ofSeconds(1)) { "pipeline.console.refresh-interval must be at least 1s" }
        require(recentReadings in 1..1000) { "pipeline.console.recent-readings must be between 1 and 1000" }
    }
}
