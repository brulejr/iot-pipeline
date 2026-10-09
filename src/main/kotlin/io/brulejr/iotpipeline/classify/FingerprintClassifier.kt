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
 * Identifies a reading's model from its structural fingerprint.
 *
 * Every reading leaves this stage with a model attached, because an unseen structure
 * registers itself. What varies is whether that model has been curated: only a model
 * with sensor mappings is [Classification.Recognised] and can have its payload parsed.
 */
class FingerprintClassifier(private val registry: ModelRegistryPort) : ClassificationPort {

    override fun classify(reading: FingerprintedReading): Classification {
        val model = registry.registerIfAbsent(reading)
        val deviceKey = DeviceKeyExtractor.from(reading.envelope)

        if (!model.recognised) {
            return Classification.Unrecognised(reading, deviceKey, model, NOT_CURATED)
        }
        // A curated model whose payload names no device leaves nothing to promote or
        // count, so it cannot be treated as recognised.
        if (deviceKey == null) {
            return Classification.Unrecognised(reading, null, model, NO_DEVICE_IDENTITY)
        }
        return Classification.Recognised(reading, deviceKey, model)
    }

    companion object {
        const val NOT_CURATED = "model has no curated sensor mappings"
        const val NO_DEVICE_IDENTITY = "payload identifies no device"
    }
}
