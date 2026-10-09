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
 * Three hashes derived from one reading, each answering a different question.
 *
 * @property event identifies this reading. Two readings with the same event hash carry
 *   the same information and only one needs processing.
 * @property device identifies the transmitter, from its model and id.
 * @property model identifies the *shape* of the payload: the hash of [modelStructure].
 *   Readings from the same kind of device share it, which is how a model is recognised
 *   without trusting a self-reported model name.
 * @property modelStructure the canonical structure the model hash was taken over, kept
 *   so a newly discovered model can be reviewed by a human.
 */
data class Fingerprint(
    val event: String,
    val device: String,
    val model: String,
    val modelStructure: String,
)

/**
 * A reading and its fingerprints, as it travels from the fingerprint stage onwards.
 */
data class FingerprintedReading(
    val envelope: SensorEnvelope,
    val fingerprint: Fingerprint,
)
