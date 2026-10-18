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

/**
 * Fails when the PostgreSQL URL or credentials still hold an unresolved placeholder.
 *
 * Spring leaves an unresolved placeholder in place when binding configuration
 * properties, so a missing `POSTGRES_APP_PASSWORD` reaches the driver as the literal
 * text `$`+`{POSTGRES_APP_PASSWORD}`. The connection then fails with an authentication
 * error that says nothing about the variable that was not set.
 *
 * Called by [DatabaseMigrator] before anything connects. The password is checked but
 * never shown.
 */
fun requireResolvedDataSource(properties: DataSourceProperties) {
    val unresolved = listOf(
        "url" to properties.url,
        "username" to properties.username,
        "password" to properties.password,
    ).filter { (_, value) -> value != null && value.contains("\${") }
    require(unresolved.isEmpty()) {
        val shown = unresolved.joinToString { (name, value) ->
            "spring.datasource.$name" + if (name == "password") "" else " ($value)"
        }
        "$shown has unresolved placeholders. Export the variables from .env " +
            "(`set -a; . ./.env; set +a`), import .env in application-local.yml " +
            "(`spring.config.import: optional:file:.env[.properties]`), or set them there."
    }
}
