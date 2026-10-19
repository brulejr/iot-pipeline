/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.store

import io.brulejr.iotpipeline.classify.DeviceKey
import io.brulejr.iotpipeline.parse.ParsedReading
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Stage 5, second half: keeps every parsed reading, the design's neutral handoff
 * boundary, and counts what was kept.
 */
class StoreService(
    private val store: ReadingStorePort,
    private val clock: Clock,
) {

    private val readings = AtomicLong()
    private val values = AtomicLong()
    private val failures = AtomicLong()
    private val lastStoredAt = AtomicReference<Instant?>()

    /**
     * Writes [reading]. A failed write is counted and rethrown, so the reading goes no
     * further and the failure reaches the ingest error log.
     */
    fun store(reading: ParsedReading) {
        try {
            store.store(reading)
        } catch (e: Exception) {
            failures.incrementAndGet()
            throw e
        }
        readings.incrementAndGet()
        values.addAndGet(reading.values.size.toLong())
        lastStoredAt.set(clock.instant())
    }

    /** What readings are written to. Throws when the store cannot be reached. */
    fun describe(): String = store.describe()

    /** The latest values stored for [deviceKey], newest first, at most [limit] of them. */
    fun recent(deviceKey: DeviceKey, limit: Int): List<StoredValue> = store.recent(deviceKey, limit)

    fun stats() = StoreStats(readings.get(), values.get(), failures.get(), lastStoredAt.get())
}
