/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** How promotion is set up: where approvals are kept and where they are restored from. */
class PromotionInfoContributor(private val datafill: PromotionDatafill) : InfoContributor {

    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "promotion",
            mapOf(
                "registry" to datafill.registry,
                "seed" to mapOf("enabled" to datafill.seed.enabled, "location" to datafill.seed.location),
            ),
        )
    }
}
