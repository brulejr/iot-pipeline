/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.pipeline

/**
 * Where a feature keeps the data it accumulates.
 *
 * Chosen through each feature's own `*Datafill` rather than a property condition: this
 * picks an implementation, it does not switch a feature on or off.
 */
enum class RegistryType {
    /** Persisted in PostgreSQL, surviving a restart. */
    POSTGRES,

    /** Held for the lifetime of the process; needs no database. What the tests use. */
    MEMORY,
}
