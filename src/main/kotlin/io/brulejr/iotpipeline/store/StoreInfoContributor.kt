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

/** Where readings are stored and for how long. The token is deliberately left out. */
class StoreInfoContributor(private val datafill: StoreDatafill) : InfoContributor {

    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "store",
            mapOf(
                "enabled" to datafill.enabled,
                "url" to datafill.url,
                "database" to datafill.database,
                "measurement" to datafill.measurement,
                // What the database is created with; see StoreDatafill.retention.
                "retention" to datafill.retention,
            ),
        )
    }
}
