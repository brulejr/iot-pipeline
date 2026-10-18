/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.store

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** Where readings are stored and for how long. */
class StoreInfoContributor(private val datafill: StoreDatafill) : InfoContributor {

    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "store",
            mapOf(
                "enabled" to datafill.enabled,
                "table" to "readings",
                "retention" to datafill.retention.toString(),
            ),
        )
    }
}
