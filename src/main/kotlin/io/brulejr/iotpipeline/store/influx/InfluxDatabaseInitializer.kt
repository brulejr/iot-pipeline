/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.store.influx

import io.brulejr.iotpipeline.store.StoreDatafill
import org.springframework.beans.factory.InitializingBean
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.client.RestClient

/**
 * Creates the readings database with its retention period, if it is not there already.
 *
 * This exists because InfluxDB 3 creates a database implicitly on first write, and an
 * implicitly created one has no retention period at all. The design requires readings to
 * age out, so leaving creation to the first write would quietly keep everything for
 * ever. Creating it here, before anything is written, is also what makes the database
 * owned solely by this application.
 *
 * A retention period is fixed when a database is created, so changing
 * `pipeline.store.retention` afterwards has no effect on an existing database; the log
 * says as much when it finds one.
 *
 * Runs as part of bean initialisation rather than from an `ApplicationRunner`, because
 * Spring Integration starts its endpoints at the end of the context refresh, after every
 * singleton is initialised but *before* runners execute. From a runner this lost the
 * race: the MQTT adapter was already delivering, and the first write created the
 * database implicitly, without a retention period. Startup therefore fails if InfluxDB
 * cannot be reached, which is deliberate: carrying on would mean readings landing in a
 * database that keeps them for ever.
 */
class InfluxDatabaseInitializer(private val datafill: StoreDatafill) : InitializingBean {

    override fun afterPropertiesSet() = createIfAbsent()

    private val log = LoggerFactory.getLogger(InfluxDatabaseInitializer::class.java)

    // Built here rather than injected: these two admin calls need nothing the
    // auto-configured builder would add, and it is not a bean in this application.
    private val rest = RestClient.builder()
        .baseUrl(datafill.url)
        .defaultHeader("Authorization", "Bearer ${datafill.token}")
        .build()

    fun createIfAbsent() {
        if (datafill.database in existingDatabases()) {
            log.info(
                "InfluxDB database '{}' already exists; its retention period was fixed when it " +
                    "was created and pipeline.store.retention does not change it",
                datafill.database,
            )
            return
        }
        rest.post()
            .uri("/api/v3/configure/database")
            .body(mapOf("db" to datafill.database, "retention_period" to datafill.retention))
            .retrieve()
            .toBodilessEntity()
        log.info(
            "Created InfluxDB database '{}' with a {} retention period",
            datafill.database, datafill.retention,
        )
    }

    private fun existingDatabases(): Set<String> {
        val rows = rest.get()
            .uri("/api/v3/configure/database?format=json")
            .retrieve()
            .body(DATABASE_ROWS)
            ?: emptyList()
        // The listing names the column by its IOx identifier.
        return rows.mapNotNull { it["iox::database"] }.toSet()
    }

    private companion object {
        val DATABASE_ROWS = object : ParameterizedTypeReference<List<Map<String, String>>>() {}
    }
}
