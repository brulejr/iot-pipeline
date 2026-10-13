/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** How classification is set up: where models are kept and where curation is restored from. */
class ClassificationInfoContributor(private val datafill: ClassificationDatafill) : InfoContributor {

    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "classification",
            mapOf(
                "registry" to datafill.registry,
                "seed" to mapOf("enabled" to datafill.seed.enabled, "location" to datafill.seed.location),
            ),
        )
    }
}
