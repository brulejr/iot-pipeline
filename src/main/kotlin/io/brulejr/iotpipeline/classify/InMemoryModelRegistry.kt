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
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory [ModelRegistryPort], for running without a database: the tests select it
 * with `pipeline.model-registry.type=memory`.
 *
 * The catalogue is rebuilt from traffic on restart and curated mappings do not survive
 * one, so nothing stays recognised. Use the MongoDB implementation to keep curation.
 */
class InMemoryModelRegistry(private val jsonMapper: JsonMapper) : ModelRegistryPort {

    private val byFingerprint = ConcurrentHashMap<String, ModelRecord>()

    override fun registerIfAbsent(reading: FingerprintedReading): ModelRecord =
        byFingerprint.computeIfAbsent(reading.fingerprint.model) {
            ModelRecord(
                fingerprint = reading.fingerprint.model,
                source = reading.envelope.source,
                name = reading.envelope.payload.get("model")?.takeIf { it.isValueNode }?.asString(),
                structure = reading.fingerprint.modelStructure,
                discoveredAt = Instant.now(),
            )
        }

    override fun all(): List<ModelRecord> = byFingerprint.values.sortedBy { it.discoveredAt }

    override fun find(fingerprint: String): ModelRecord? = byFingerprint[fingerprint]

    override fun curate(fingerprint: String, sensors: List<SensorMapping>): CurationResult {
        val model = byFingerprint[fingerprint] ?: return CurationResult.ModelNotFound
        validateCuration(model, sensors, jsonMapper)?.let { return it }
        val curated = model.copy(sensors = sensors)
        byFingerprint[fingerprint] = curated
        return CurationResult.Curated(curated)
    }
}
