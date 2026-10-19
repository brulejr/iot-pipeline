/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.monitor

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** How many streams may be open, and how long devices are remembered. */
class MonitorInfoContributor(private val datafill: MonitorDatafill) : InfoContributor {

    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "monitor",
            mapOf(
                "maxSubscribers" to datafill.maxSubscribers,
                "queueCapacity" to datafill.queueCapacity,
                "heardFor" to datafill.heardFor.toString(),
                "heardDevices" to datafill.heardDevices,
            ),
        )
    }
}
