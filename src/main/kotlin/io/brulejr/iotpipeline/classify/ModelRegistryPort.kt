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
 * Registry of every model structure the pipeline has seen.
 *
 * Models register themselves on first sighting, so the catalogue builds from live
 * traffic instead of being written out in advance. Recognising a model still takes a
 * human: someone has to supply its [ModelRecord.sensors].
 */
interface ModelRegistryPort {

    /** Returns the model for this reading's structure, registering it if it is new. */
    fun registerIfAbsent(reading: FingerprintedReading): ModelRecord

    /** Every model seen so far, newest discovery last. */
    fun all(): List<ModelRecord>

    /**
     * Inserts [model] whole, mappings included, unless its fingerprint is already known.
     * Returns true if it was inserted.
     *
     * Exists for restoring a model from a seed file: [registerIfAbsent] builds one from a
     * live reading, which is not available when the store has been emptied.
     */
    fun insertIfAbsent(model: ModelRecord): Boolean

    /** The model with this structural fingerprint, or null if it has not been seen. */
    fun find(fingerprint: String): ModelRecord?

    /**
     * Replaces a model's sensor mappings, which is how a human makes it recognised.
     *
     * [category] says what kind of thing the model is and must be a real value, not
     * [ModelRecord.UNCATEGORISED]: curation is the moment someone decides.
     *
     * Every mapping must name a field the model's structure actually has. A mapping for
     * a field that is not there would make the model report itself recognised while
     * yielding nothing at parse time, and the mistake would surface in a later stage far
     * from its cause.
     */
    fun curate(fingerprint: String, category: String, sensors: List<SensorMapping>): CurationResult
}
