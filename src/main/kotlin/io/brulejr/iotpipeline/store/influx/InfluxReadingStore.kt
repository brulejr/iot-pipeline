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
import com.influxdb.v3.client.Point
import com.influxdb.v3.client.write.WriteOptions
import com.influxdb.v3.client.write.WritePrecision
import io.brulejr.iotpipeline.parse.ParsedReading
import io.brulejr.iotpipeline.parse.SensorValue
import io.brulejr.iotpipeline.store.ReadingStorePort
import io.brulejr.iotpipeline.store.StoreDatafill

/**
 * Writes parsed readings to InfluxDB 3.
 *
 * One point per value rather than one per reading, so each sensor is a series in its own
 * right: a model gaining or losing a mapping does not reshape what is already stored,
 * and a query for temperature need not know which models happen to report it. All of a
 * reading's points go in one call.
 *
 * Tags are what is worth filtering or grouping by; the value itself is the only field.
 * Analog and binary values use different field names because InfluxDB fixes a field's
 * type on first write, so one `value` field could not hold both.
 */
class InfluxReadingStore(
    private val client: InfluxDBClient,
    datafill: StoreDatafill,
) : ReadingStorePort {

    private val database = datafill.database
    private val measurement = datafill.measurement
    private val writeOptions = WriteOptions(datafill.database, WritePrecision.NS, null)

    override fun store(reading: ParsedReading) {
        if (reading.values.isEmpty()) return
        client.writePoints(reading.values.map { pointFor(reading, it) }, writeOptions)
    }

    /** Asks the server for its version, which needs no database and proves it answers. */
    override fun describe() = "InfluxDB ${client.serverVersion}, database $database"

    private fun pointFor(reading: ParsedReading, value: SensorValue): Point {
        val point = Point.measurement(measurement)
            .setTag("source", reading.device.deviceKey.source)
            .setTag("device", reading.device.deviceKey.id)
            .setTag("name", reading.device.name)
            .setTag("type", reading.device.type)
            .setTag("area", reading.device.area)
            .setTag("model", reading.model.name ?: "unnamed")
            .setTag("category", reading.model.category)
            .setTag("sensor", value.mapping.classname)
            .setTimestamp(reading.receivedAt)
        return when (value) {
            is SensorValue.Analog -> point.setField("value", value.value)
            is SensorValue.Binary -> point.setField("state", value.on)
        }
    }
}
