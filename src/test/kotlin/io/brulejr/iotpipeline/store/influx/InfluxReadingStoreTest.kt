/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.store.influx

import com.influxdb.v3.client.InfluxDBClient
import io.brulejr.iotpipeline.classify.DeviceKey
import io.brulejr.iotpipeline.classify.ModelRecord
import io.brulejr.iotpipeline.classify.SensorMapping
import io.brulejr.iotpipeline.classify.SensorType
import io.brulejr.iotpipeline.parse.ParsedReading
import io.brulejr.iotpipeline.parse.SensorValue
import io.brulejr.iotpipeline.promote.PromotedDevice
import io.brulejr.iotpipeline.store.StoreDatafill
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.time.Instant
import kotlin.test.assertEquals

class InfluxReadingStoreTest {

    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private val temperature = SensorValue.Analog(SensorMapping("temperature_C", SensorType.ANALOG, "temperature"), 21.5)

    /** pointFor never touches the client, so any call to it is a test failure. */
    private val client = Proxy.newProxyInstance(
        javaClass.classLoader,
        arrayOf(InfluxDBClient::class.java),
    ) { _, method, _ -> error("unexpected call to ${method.name}") } as InfluxDBClient

    private val store = InfluxReadingStore(client, StoreDatafill(token = "unused"))

    private fun reading(name: String, area: String) = ParsedReading(
        device = PromotedDevice(DeviceKey("rtl433", "Acurite-Tower/A/3064"), name, "thermometer", area, now),
        model = ModelRecord("fp", "rtl433", "Acurite-Tower", "{}", now, "weather"),
        receivedAt = now,
        values = listOf(temperature),
    )

    /** Tag set of a line-protocol point: everything between the measurement and the first space. */
    private fun tagsOf(line: String) = line.substringBefore(' ').substringAfter(',')

    @Test
    fun `the labels a person chose are fields, not tags`() {
        val line = store.pointFor(reading("Lab Weather sensor", "lab"), temperature)
            .toLineProtocol()

        assertEquals("category=weather,device=Acurite-Tower/A/3064,model=Acurite-Tower,sensor=temperature,source=rtl433", tagsOf(line))
        val fields = line.substringAfter(' ')
        listOf("device_name=\"Lab Weather sensor\"", "device_type=\"thermometer\"", "device_area=\"lab\"", "value=21.5")
            .forEach { assert(it in fields) { "$it missing from $fields" } }
    }

    @Test
    fun `relabelling a device keeps its series`() {
        val before = store.pointFor(reading("Back garden sensor", "garden"), temperature)
        val after = store.pointFor(reading("Lab Weather sensor", "lab"), temperature)

        assertEquals(tagsOf(before.toLineProtocol()), tagsOf(after.toLineProtocol()))
    }
}
