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
 * Registry of every model structure the pipeline has seen. Storage only; the rules
 * about what may be stored live in [ClassificationService].
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
     * Stores a model's curation, returning the updated model, or null if no model has
     * that fingerprint. Storage only: [ClassificationService.curate] decides what is
     * acceptable before this is called.
     */
    fun updateCuration(fingerprint: String, category: String, sensors: List<SensorMapping>): ModelRecord?

    /**
     * Forgets a model, curation included. Returns true if it was known. A model whose
     * devices still transmit registers itself again, uncurated, from their next reading.
     */
    fun delete(fingerprint: String): Boolean
}
