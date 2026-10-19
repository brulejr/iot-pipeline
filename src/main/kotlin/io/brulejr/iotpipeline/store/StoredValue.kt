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
 * One value as it was stored: a row of the `readings` table, read back.
 *
 * @property sensor the mapping's class, e.g. `temperature`.
 * @property field the payload field it was read from, e.g. `temperature_C`.
 * @property value the measurement, for an analog sensor; null for a binary one.
 * @property state the state, for a binary sensor, inversion already applied; null for an
 *   analog one.
 */
data class StoredValue(
    val time: Instant,
    val sensor: String,
    val field: String,
    val value: Double?,
    val state: Boolean?,
)
