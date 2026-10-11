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
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InfluxDatabaseInitializerTest {

    @Test
    fun `a blank token fails before any request, naming where it comes from`() {
        // Nothing listens on this port; reaching the network would fail differently.
        val initializer = InfluxDatabaseInitializer(StoreDatafill(url = "http://127.0.0.1:1", token = " "))

        val failure = assertFailsWith<IllegalStateException> { initializer.afterPropertiesSet() }

        assertTrue(failure.message!!.contains("INFLUXDB3_TOKEN"), failure.message!!)
        assertTrue(failure.message!!.contains("pipeline.store.enabled=false"), failure.message!!)
    }
}
