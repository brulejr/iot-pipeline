/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.pipeline

import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals

@Testcontainers(disabledWithoutDocker = true)
class DatabaseMigratorTest {

    private val db = PostgresTestDatabase

    @Test
    fun `every table is created in the application's own schema`() {
        val tables = db.jdbc
            .sql("SELECT table_name FROM information_schema.tables WHERE table_schema = :schema ORDER BY 1")
            .param("schema", PostgresTestDatabase.SCHEMA)
            .query(String::class.java)
            .list()

        assertEquals(
            listOf("flyway_schema_history", "models", "promoted_devices", "readings", "sightings"),
            tables,
        )
        val inPublic = db.jdbc
            .sql("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'")
            .query(Long::class.java)
            .single()
        assertEquals(0L, inPublic, "nothing belongs in the public schema")
    }

    @Test
    fun `migrating again changes nothing`() {
        val applied = { db.jdbc.sql("SELECT count(*) FROM flyway_schema_history").query(Long::class.java).single() }
        val before = applied()

        db.migrator.migrate()

        assertEquals(before, applied())
    }
}
