/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify

import io.brulejr.iotpipeline.fingerprint.FingerprintedReading

/**
 * Port for the classification stage: identifies the model of the device that sent a
 * reading, and so which sensor mappings parse its payload.
 *
 * Identifying the model is the whole of this stage. Whether the *device* is known is a
 * separate question answered by promotion, which is a manual step.
 *
 * Implementations are swappable. Register one as a bean to replace
 * [FingerprintClassifier].
 */
fun interface ClassificationPort {
    fun classify(reading: FingerprintedReading): Classification
}
