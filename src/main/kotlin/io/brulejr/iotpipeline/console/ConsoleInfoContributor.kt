/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.console

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** Where the console is served and how often its pages refresh. */
class ConsoleInfoContributor(private val datafill: ConsoleDatafill) : InfoContributor {

    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "console",
            mapOf(
                "path" to "/ui",
                "refreshInterval" to datafill.refreshInterval.toString(),
                "recentReadings" to datafill.recentReadings,
            ),
        )
    }
}
