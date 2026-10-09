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
 * The outcome of classification. A model is always attached, since an unseen payload
 * structure registers itself as a new model.
 */
sealed interface Classification {
    val reading: FingerprintedReading
    val deviceKey: DeviceKey?
    val model: ModelRecord

    /**
     * The model carries curated sensor mappings, so this reading can be parsed once its
     * device has been promoted to known.
     */
    data class Recognised(
        override val reading: FingerprintedReading,
        override val deviceKey: DeviceKey,
        override val model: ModelRecord,
    ) : Classification

    /**
     * The model is registered but cannot be parsed yet. The reading still goes to the
     * recommendation engine, which looks for patterns in raw payloads.
     */
    data class Unrecognised(
        override val reading: FingerprintedReading,
        override val deviceKey: DeviceKey?,
        override val model: ModelRecord,
        val reason: String,
    ) : Classification
}
