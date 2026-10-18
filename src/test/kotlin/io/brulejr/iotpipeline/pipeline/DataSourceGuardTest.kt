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
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataSourceGuardTest {

    private fun properties(
        url: String = "jdbc:postgresql://localhost:5432/iot_pipeline",
        username: String = "iot_pipeline",
        password: String = "secret",
    ) = DataSourceProperties().apply {
        this.url = url
        this.username = username
        this.password = password
    }

    @Test
    fun `resolved settings pass`() {
        assertDoesNotThrow { requireResolvedDataSource(properties()) }
    }

    @Test
    fun `an unresolved database name is refused`() {
        val failure = assertThrows<IllegalArgumentException> {
            requireResolvedDataSource(properties(url = "jdbc:postgresql://localhost:5432/\${POSTGRES_DB}"))
        }
        assertTrue("spring.datasource.url" in failure.message!!)
    }

    @Test
    fun `an unresolved password is refused without being shown`() {
        val failure = assertThrows<IllegalArgumentException> {
            requireResolvedDataSource(properties(password = "\${POSTGRES_APP_PASSWORD}"))
        }
        assertTrue("spring.datasource.password" in failure.message!!)
        assertFalse("POSTGRES_APP_PASSWORD" in failure.message!!)
    }
}
