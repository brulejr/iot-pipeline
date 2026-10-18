/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.pipeline

import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

// The PostgreSQL driver maps `timestamptz` to OffsetDateTime but has no mapping for
// Instant, the type the pipeline uses throughout. These convert at the boundary.

/** This instant as a `timestamptz` parameter, in UTC. */
fun Instant.toTimestamptz(): OffsetDateTime = atOffset(ZoneOffset.UTC)

/** The `timestamptz` in [column] as an instant, or null if the column is null. */
fun ResultSet.getInstant(column: String): Instant? =
    getObject(column, OffsetDateTime::class.java)?.toInstant()
