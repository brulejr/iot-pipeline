/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.pipeline

import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * A throwaway PostgreSQL in a container, started once and shared by every test that
 * needs one, with the application's schema migrated into it.
 *
 * Tests using it carry `@Testcontainers(disabledWithoutDocker = true)`, so a machine
 * without Docker skips them rather than failing. Nothing here ever reaches a real
 * database.
 */
object PostgresTestDatabase {

    const val SCHEMA = "iot_pipeline"

    private val container = PostgreSQLContainer("postgres:17").apply { start() }

    /** Connections in the application's schema, as `spring.datasource.hikari.schema` arranges. */
    val dataSource = DriverManagerDataSource(
        container.jdbcUrl + (if ('?' in container.jdbcUrl) "&" else "?") + "currentSchema=$SCHEMA",
        container.username,
        container.password,
    )

    val migrator = DatabaseMigrator(
        dataSource,
        DataSourceProperties().apply {
            url = container.jdbcUrl
            username = container.username
            password = container.password
        },
        DatabaseDatafill(SCHEMA),
    ).also { it.migrate() }

    val jdbc: JdbcClient = JdbcClient.create(dataSource)

    val namedJdbc = NamedParameterJdbcTemplate(dataSource)

    val transactions = TransactionTemplate(DataSourceTransactionManager(dataSource))

    /** Empties every table, so each test starts from nothing. */
    fun reset() {
        jdbc.sql("TRUNCATE models, promoted_devices, sightings, readings").update()
    }
}
