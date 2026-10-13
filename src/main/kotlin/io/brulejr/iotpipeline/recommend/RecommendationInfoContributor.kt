/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.recommend

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** How devices are ranked for promotion, so a recommendation can be read against it. */
class RecommendationInfoContributor(private val datafill: RecommendationDatafill) : InfoContributor {

    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "recommendation",
            mapOf(
                "registry" to datafill.registry,
                "enabled" to datafill.enabled,
                "windowHours" to datafill.windowHours,
                "minHoursHeard" to datafill.minHoursHeard,
                "signalField" to datafill.signalField,
                "signalFloor" to datafill.signalFloor,
                "signalCeiling" to datafill.signalCeiling,
                "proximityWeight" to datafill.proximityWeight,
                "minProximity" to datafill.minProximity,
            ),
        )
    }
}
