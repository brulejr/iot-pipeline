/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify

import io.brulejr.iotpipeline.ingest.SensorEnvelope

/**
 * Port for the classification stage: identifies the model of the device that sent a
 * reading, and so which rule set parses its payload.
 *
 * Identifying the model is the whole of this stage. Whether the device is *known* is
 * a separate question answered by promotion, which is a manual step.
 *
 * Implementations are swappable (a rules engine, hand-rolled pattern matching, ...).
 * The envelope's source is one input; rules may be cross-source or source-aligned.
 * Register an implementation as a bean to replace [RuleBasedClassifier].
 */
fun interface ClassificationPort {
    fun classify(envelope: SensorEnvelope): Classification
}

/** Stable identity of a physical device as seen by one source. */
data class DeviceKey(val source: String, val id: String)

sealed interface Classification {
    val envelope: SensorEnvelope
    val deviceKey: DeviceKey?

    /**
     * The device's model was recognised. [parseRuleSetId] names the rule set that
     * parses payloads from this model, applied once the device has been promoted to
     * known. Recognising the model does not make the device known.
     */
    data class Identified(
        override val envelope: SensorEnvelope,
        override val deviceKey: DeviceKey,
        val model: String,
        val parseRuleSetId: String,
    ) : Classification

    /**
     * No rule recognised the model. The reading still goes to the recommendation
     * engine, which looks for patterns in raw payloads. [deviceKey] is null when the
     * payload carries too little to identify a device at all.
     */
    data class Unidentified(
        override val envelope: SensorEnvelope,
        override val deviceKey: DeviceKey?,
        val reason: String,
    ) : Classification
}
