/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.dedupe

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** How long readings are remembered, and for how many transmitters. */
class DedupeInfoContributor(private val datafill: DedupeDatafill) : InfoContributor {

    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "dedupe",
            mapOf(
                "enabled" to datafill.enabled,
                "window" to datafill.window.toString(),
                "maximumDevices" to datafill.maximumDevices,
            ),
        )
    }
}
