/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.store

import io.brulejr.iotpipeline.parse.ParsedReading

/**
 * Keeps nothing. Selected by `pipeline.store.enabled=false`, and what the tests use so
 * they need no database; readings are still parsed and logged on the way past.
 */
class DiscardingReadingStore : ReadingStorePort {
    override fun store(reading: ParsedReading) = Unit
}
