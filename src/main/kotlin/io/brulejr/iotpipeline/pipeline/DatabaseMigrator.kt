/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.pipeline

import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
import javax.sql.DataSource

/**
 * Brings the application's PostgreSQL schema up to date, the first time anything needs it.
 *
 * Flyway is run here rather than through Spring Boot's auto-configuration, which migrates
 * at startup unconditionally: a context with every registry in memory and storage off
 * would then still need a database. Each feature that chooses PostgreSQL calls [migrate]
 * while its beans are created, so nothing connects unless something uses it, and the
 * tables exist before the first reading arrives.
 *
 * That also means startup fails when PostgreSQL is chosen but cannot be reached, which is
 * deliberate: a registry that cannot keep what it is given would quietly lose approvals.
 */
class DatabaseMigrator(
    private val dataSource: DataSource,
    private val dataSourceProperties: DataSourceProperties,
    private val datafill: DatabaseDatafill,
) {

    private val log = LoggerFactory.getLogger(DatabaseMigrator::class.java)

    // Lazy, and so synchronised: whichever feature asks first runs the migration, and the
    // rest find it done.
    private val migration by lazy {
        requireResolvedDataSource(dataSourceProperties)
        val result = Flyway.configure()
            .dataSource(dataSource)
            .schemas(datafill.schema)
            .defaultSchema(datafill.schema)
            .locations(LOCATION)
            .load()
            .migrate()
        log.info(
            "PostgreSQL schema '{}' is at version {} ({} migration(s) applied)",
            datafill.schema, result.targetSchemaVersion ?: result.initialSchemaVersion, result.migrationsExecuted,
        )
    }

    /** Runs any pending migrations, once per process. */
    fun migrate() = migration

    private companion object {
        const val LOCATION = "classpath:db/migration"
    }
}
