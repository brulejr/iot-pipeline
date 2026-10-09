/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.fingerprint

import io.brulejr.iotpipeline.ingest.SensorEnvelope

/**
 * A reading and its fingerprints, as it travels from the fingerprint stage onwards.
 */
data class FingerprintedReading(
    val envelope: SensorEnvelope,
    val fingerprint: Fingerprint,
)
