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

/**
 * Where parsed readings are kept.
 *
 * Storage is the design's neutral handoff boundary: what is written here is
 * consumer-agnostic, and the stages after it reshape rather than re-derive.
 */
interface ReadingStorePort {

    /** Writes every value in [reading]. */
    fun store(reading: ParsedReading)

    /**
     * Names what readings are written to, asking it if need be, so a store that cannot
     * be reached throws.
     */
    fun describe(): String

    /** The latest values stored for [deviceKey], newest first, at most [limit] of them. */
    fun recent(deviceKey: DeviceKey, limit: Int): List<StoredValue>
}
