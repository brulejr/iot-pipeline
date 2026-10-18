/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.store.postgres

import io.brulejr.iotpipeline.classify.DeviceKey
import io.brulejr.iotpipeline.classify.ModelRecord
import io.brulejr.iotpipeline.classify.SensorMapping
import io.brulejr.iotpipeline.classify.SensorType
import io.brulejr.iotpipeline.parse.ParsedReading
import io.brulejr.iotpipeline.parse.SensorValue
import io.brulejr.iotpipeline.pipeline.PostgresTestDatabase
import io.brulejr.iotpipeline.pipeline.getInstant
import io.brulejr.iotpipeline.promote.PromotedDevice
import io.brulejr.iotpipeline.store.StoreDatafill
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers(disabledWithoutDocker = true)
class PostgresReadingStoreTest {

    private val now = Instant.parse("2026-10-18T12:00:00Z")
    private val db = PostgresTestDatabase
    private val store = PostgresReadingStore(
        db.namedJdbc,
        db.transactions,
        StoreDatafill(retention = Duration.ofDays(30)),
        Clock.fixed(now, ZoneOffset.UTC),
    )

    private val temperature = SensorMapping("temperature_C", SensorType.ANALOG, "temperature", "Temperature")
    private val battery = SensorMapping("battery_ok", SensorType.BINARY, "battery", "Battery", inverted = true)

    @BeforeEach
    fun reset() = PostgresTestDatabase.reset()

    private fun reading(at: Instant = now, name: String = "Lab Climate", area: String = "lab") = ParsedReading(
        device = PromotedDevice(DeviceKey("rtl433", "Acurite-Tower/A/3064"), name, "thermometer", area, now),
        model = ModelRecord("fp", "rtl433", "Acurite-Tower", "{}", now, "weather", listOf(temperature, battery)),
        receivedAt = at,
        values = listOf(SensorValue.Analog(temperature, 21.5), SensorValue.Binary(battery, false)),
    )

    private fun rows() = db.jdbc
        .sql("SELECT * FROM readings ORDER BY field DESC")
        .query { rs, _ ->
            mapOf(
                "time" to rs.getInstant("time"),
                "series" to listOf("source", "device", "model", "category", "sensor", "field").map(rs::getString),
                "labels" to listOf("device_name", "device_type", "device_area").map(rs::getString),
                "value" to rs.getObject("value"),
                "state" to rs.getObject("state"),
            )
        }
        .list()

    @Test
    fun `each value is a row, analog in value and binary in state`() {
        store.store(reading())

        val (temp, batt) = rows()
        assertEquals(now, temp["time"])
        assertEquals(listOf("rtl433", "Acurite-Tower/A/3064", "Acurite-Tower", "weather", "temperature", "temperature_C"), temp["series"])
        assertEquals(listOf("Lab Climate", "thermometer", "lab"), temp["labels"])
        assertEquals(21.5, temp["value"])
        assertNull(temp["state"])
        assertEquals(false, batt["state"])
        assertNull(batt["value"])
    }

    @Test
    fun `relabelling a device keeps its series and its history's labels`() {
        store.store(reading(now.minusSeconds(60), "Back garden sensor", "garden"))
        store.store(reading(now, "Lab Climate", "lab"))

        val temps = rows().filter { (it["series"] as List<*>).last() == "temperature_C" }.sortedBy { it["time"] as Instant }
        assertEquals(1, temps.map { it["series"] }.distinct().size)
        assertEquals(listOf("garden", "lab"), temps.map { (it["labels"] as List<*>).last() })
    }

    @Test
    fun `readings past the retention period are deleted as new ones are stored`() {
        db.jdbc.sql(
            """
            INSERT INTO readings (time, source, device, model, category, sensor, field, value,
                                  device_name, device_type, device_area)
            VALUES (now() - interval '400 days', 'rtl433', 'old', 'm', 'c', 's', 'f', 1, 'n', 't', 'a')
            """,
        ).update()

        store.store(reading())

        assertTrue(rows().none { (it["series"] as List<*>)[1] == "old" })
        assertEquals(2, rows().size)
    }

    @Test
    fun `the store names the server it reached`() {
        assertTrue(store.describe().startsWith("PostgreSQL 17"), store.describe())
    }
}
