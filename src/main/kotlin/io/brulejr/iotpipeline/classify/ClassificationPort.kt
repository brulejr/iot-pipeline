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
 * Port for the classification stage: decides what kind of device sent a reading and
 * which rule set decodes its payload.
 *
 * Implementations are swappable (a rules engine, hand-rolled pattern matching, ...).
 * The envelope's source is one input; rules may be cross-source or source-aligned.
 * Register an implementation as a bean to replace [NoRulesClassifier].
 */
fun interface ClassificationPort {
    fun classify(envelope: SensorEnvelope): Classification
}

/** Stable identity of a physical device as seen by one source. */
data class DeviceKey(val source: String, val id: String)

sealed interface Classification {
    val envelope: SensorEnvelope
    val deviceKey: DeviceKey?

    /** A curated device; [ruleSetId] names the rule set that decodes its payload. */
    data class Known(
        override val envelope: SensorEnvelope,
        override val deviceKey: DeviceKey,
        val deviceType: String,
        val ruleSetId: String,
    ) : Classification

    /**
     * Not a curated device; candidate for the recommendation engine. [deviceKey] is
     * null when the payload carries too little to identify a device at all.
     */
    data class Unknown(
        override val envelope: SensorEnvelope,
        override val deviceKey: DeviceKey?,
        val reason: String,
    ) : Classification
}
