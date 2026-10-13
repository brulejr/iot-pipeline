/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.store

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @property enabled when false, readings are parsed and then dropped. Useful for
 *   watching the pipeline without writing anything.
 * @property url where InfluxDB 3 is listening.
 * @property token admin token; see `scripts/bootstrap.sh`.
 * @property database the database readings are written to. Owned solely by this
 *   application, as the design requires.
 * @property retention how long a reading is kept. Applied when this application creates
 *   the database; InfluxDB will not change it afterwards, so altering this value has no
 *   effect on a database that already exists.
 * @property measurement the table readings land in.
 */
@ConfigurationProperties("pipeline.store")
data class StoreDatafill(
    val enabled: Boolean = true,
    val url: String = "http://localhost:8181",
    val token: String = "",
    val database: String = "sensors",
    val retention: String = "30d",
    val measurement: String = "reading",
) {
    init {
        require(url.isNotBlank()) { "pipeline.store.url must not be blank" }
        require(database.isNotBlank()) { "pipeline.store.database must not be blank" }
        require(measurement.isNotBlank()) { "pipeline.store.measurement must not be blank" }
        // Left blank, the database would be created with no retention period at all,
        // which is what creating it here exists to prevent.
        require(retention.isNotBlank()) { "pipeline.store.retention must not be blank" }
        // The token is checked when the database is created, and only when storage is
        // enabled, so a context with storage off needs none.
    }
}
