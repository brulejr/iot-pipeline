/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify.postgres

import io.brulejr.iotpipeline.classify.ModelRecord
import io.brulejr.iotpipeline.classify.ModelRegistryPort
import io.brulejr.iotpipeline.classify.SensorMapping
import io.brulejr.iotpipeline.fingerprint.FingerprintedReading
import io.brulejr.iotpipeline.pipeline.getInstant
import io.brulejr.iotpipeline.pipeline.toTimestamptz
import org.springframework.jdbc.core.simple.JdbcClient
import tools.jackson.databind.json.JsonMapper
import java.sql.ResultSet
import java.time.Instant

/**
 * PostgreSQL-backed [ModelRegistryPort], so a discovered model and the sensor mappings
 * curated against it survive a restart.
 *
 * The structural fingerprint is the primary key, so the table cannot hold two rows for
 * one structure. The mappings are a JSON column, read and replaced whole.
 */
class PostgresModelRegistry(
    private val jdbc: JdbcClient,
    private val jsonMapper: JsonMapper,
) : ModelRegistryPort {

    override fun registerIfAbsent(reading: FingerprintedReading): ModelRecord {
        val fingerprint = reading.fingerprint.model
        // Almost every reading is of a model already known, so look before writing.
        find(fingerprint)?.let { return it }

        insertIfAbsent(
            ModelRecord(
                fingerprint = fingerprint,
                source = reading.envelope.source,
                name = reading.envelope.payload.get("model")?.takeIf { it.isValueNode }?.asString(),
                structure = reading.fingerprint.modelStructure,
                discoveredAt = Instant.now(),
            ),
        )
        // Whichever thread inserted it, the stored row is the one to return.
        return checkNotNull(find(fingerprint)) { "model $fingerprint vanished after it was registered" }
    }

    override fun all(): List<ModelRecord> = jdbc
        .sql("SELECT * FROM models ORDER BY discovered_at, fingerprint")
        .query { rs, _ -> rs.toRecord() }
        .list()

    override fun insertIfAbsent(model: ModelRecord): Boolean = jdbc
        .sql(
            """
            INSERT INTO models (fingerprint, source, name, structure, discovered_at, category, sensors)
            VALUES (:fingerprint, :source, :name, :structure, :discoveredAt, :category, CAST(:sensors AS jsonb))
            ON CONFLICT (fingerprint) DO NOTHING
            """,
        )
        .param("fingerprint", model.fingerprint)
        .param("source", model.source)
        .param("name", model.name)
        .param("structure", model.structure)
        .param("discoveredAt", model.discoveredAt.toTimestamptz())
        .param("category", model.category)
        .param("sensors", sensorsJson(model.sensors))
        .update() > 0

    override fun find(fingerprint: String): ModelRecord? = jdbc
        .sql("SELECT * FROM models WHERE fingerprint = :fingerprint")
        .param("fingerprint", fingerprint)
        .query { rs, _ -> rs.toRecord() }
        .optional()
        .orElse(null)

    override fun updateCuration(fingerprint: String, category: String, sensors: List<SensorMapping>): ModelRecord? {
        val updated = jdbc
            .sql("UPDATE models SET category = :category, sensors = CAST(:sensors AS jsonb) WHERE fingerprint = :fingerprint")
            .param("category", category)
            .param("sensors", sensorsJson(sensors))
            .param("fingerprint", fingerprint)
            .update()
        if (updated == 0) return null
        return find(fingerprint)
    }

    override fun delete(fingerprint: String): Boolean = jdbc
        .sql("DELETE FROM models WHERE fingerprint = :fingerprint")
        .param("fingerprint", fingerprint)
        .update() > 0

    private fun sensorsJson(sensors: List<SensorMapping>): String =
        jsonMapper.writeValueAsString(sensors.map { StoredSensorMapping.of(it) })

    private fun ResultSet.toRecord() = ModelRecord(
        fingerprint = getString("fingerprint"),
        source = getString("source"),
        name = getString("name"),
        structure = getString("structure"),
        discoveredAt = checkNotNull(getInstant("discovered_at")),
        category = getString("category"),
        sensors = jsonMapper.readValue(getString("sensors"), Array<StoredSensorMapping>::class.java)
            .map { it.toMapping() },
    )
}
