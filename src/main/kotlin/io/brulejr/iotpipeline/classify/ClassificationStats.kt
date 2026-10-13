/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify

/** Running totals since startup, for the health endpoint. */
data class ClassificationStats(
    val classified: Long,
    val recognised: Long,
    val unrecognised: Long,
)
