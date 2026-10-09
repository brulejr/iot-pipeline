/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify.mongo

import io.brulejr.iotpipeline.classify.ModelRecord
import io.brulejr.iotpipeline.classify.ModelRegistryPort
import io.brulejr.iotpipeline.classify.SensorMapping
import io.brulejr.iotpipeline.fingerprint.FingerprintedReading
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoOperations
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.time.Instant

/**
 * MongoDB-backed [ModelRegistryPort], so a discovered model and the sensor mappings
 * curated against it survive a restart.
 */
class MongoModelRegistry(private val mongo: MongoOperations) : ModelRegistryPort {

    override fun registerIfAbsent(reading: FingerprintedReading): ModelRecord {
        val fingerprint = reading.fingerprint.model
        find(fingerprint)?.let { return it }

        val discovered = ModelDocument(
            fingerprint = fingerprint,
            source = reading.envelope.source,
            name = reading.envelope.payload.get("model")?.takeIf { it.isValueNode }?.asString(),
            structure = reading.fingerprint.modelStructure,
            discoveredAt = Instant.now(),
        )
        return try {
            mongo.insert(discovered).toRecord()
        } catch (_: DuplicateKeyException) {
            // Another thread registered the same structure between the read and the
            // insert. The fingerprint is the id, so that document is the one we wanted.
            find(fingerprint) ?: discovered.toRecord()
        }
    }

    override fun all(): List<ModelRecord> = mongo
        .find(Query().with(Sort.by(Sort.Direction.ASC, "discoveredAt")), ModelDocument::class.java)
        .map { it.toRecord() }

    override fun find(fingerprint: String): ModelRecord? =
        mongo.findById(fingerprint, ModelDocument::class.java)?.toRecord()

    override fun curate(fingerprint: String, sensors: List<SensorMapping>): ModelRecord? {
        val updated = mongo.update(ModelDocument::class.java)
            .matching(Query(Criteria.where("_id").`is`(fingerprint)))
            .apply(Update().set("sensors", sensors.map { SensorMappingDocument.of(it) }))
            .first()
        // Nothing matched, so no model carries that fingerprint.
        if (updated.matchedCount == 0L) return null
        return find(fingerprint)
    }
}
