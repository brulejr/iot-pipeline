/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify.web

import io.brulejr.iotpipeline.classify.SensorMapping
import io.brulejr.iotpipeline.classify.SensorType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SensorRowTest {

    @Test
    fun `rows are read in index order, skipping the gaps removed rows leave`() {
        val rows = SensorRow.fromParameters(
            mapOf(
                "category" to arrayOf("security"),
                "sensors[3].name" to arrayOf("tamper"),
                "sensors[3].type" to arrayOf("BINARY"),
                "sensors[3].classname" to arrayOf("tamper"),
                "sensors[0].name" to arrayOf("closed"),
                "sensors[0].type" to arrayOf("BINARY"),
                "sensors[0].classname" to arrayOf("opening"),
                "sensors[0].friendlyName" to arrayOf("Contact"),
                "sensors[0].inverted" to arrayOf("true"),
            ),
        )

        assertEquals(listOf(0, 3), rows.map { it.index })
        assertEquals(
            listOf(
                SensorMapping("closed", SensorType.BINARY, "opening", "Contact", inverted = true),
                SensorMapping("tamper", SensorType.BINARY, "tamper"),
            ),
            rows.map { it.toMapping() },
        )
    }

    @Test
    fun `an unknown type falls back to a measurement rather than failing the form`() {
        val row = SensorRow.fromParameters(mapOf("sensors[0].name" to arrayOf("x"), "sensors[0].type" to arrayOf("bogus"))).single()

        assertEquals(SensorType.ANALOG.name, row.type)
    }
}
