/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.transform

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** Which topics readings are shaped for. */
class TransformInfoContributor(private val datafill: TransformDatafill) : InfoContributor {

    override fun contribute(builder: Info.Builder) {
        val homeAssistant = datafill.homeAssistant
        builder.withDetail(
            "transform",
            mapOf(
                "enabled" to datafill.enabled,
                "homeAssistant" to mapOf(
                    "discoveryPrefix" to homeAssistant.discoveryPrefix,
                    "statePrefix" to homeAssistant.statePrefix,
                    "originName" to homeAssistant.originName,
                    "retainState" to homeAssistant.retainState,
                ),
            ),
        )
    }
}
