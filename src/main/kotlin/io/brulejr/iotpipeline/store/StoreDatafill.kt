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
import java.time.Duration

/**
 * Readings go to the `readings` table in PostgreSQL, over Spring Boot's
 * `spring.datasource` connection, in the schema named by `pipeline.database.schema`.
 *
 * @property enabled when false, readings are parsed and then dropped. Useful for
 *   watching the pipeline without writing anything.
 * @property retention how long a reading is kept. Read every time expired readings are
 *   deleted, so a change applies from the next restart to everything already stored.
 */
@ConfigurationProperties("pipeline.store")
data class StoreDatafill(
    val enabled: Boolean = true,
    val retention: Duration = Duration.ofDays(30),
) {
    init {
        require(retention.isPositive) { "pipeline.store.retention must be positive" }
    }
}
