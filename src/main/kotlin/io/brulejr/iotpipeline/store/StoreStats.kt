/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.store

import java.time.Instant

/**
 * What has been written since startup.
 *
 * @property readings readings stored.
 * @property values sensor values across those readings, one point each.
 * @property failures readings whose write failed; each is logged on the ingest error log.
 * @property lastStoredAt when a reading was last stored, or null if none has been.
 */
data class StoreStats(
    val readings: Long,
    val values: Long,
    val failures: Long,
    val lastStoredAt: Instant?,
)
