/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.parse

import io.brulejr.iotpipeline.classify.Classification
import io.brulejr.iotpipeline.classify.DeviceKey
import io.brulejr.iotpipeline.classify.ModelRecord
import io.brulejr.iotpipeline.classify.SensorMapping
import io.brulejr.iotpipeline.classify.SensorType
import io.brulejr.iotpipeline.fingerprint.Fingerprint
import io.brulejr.iotpipeline.fingerprint.FingerprintedReading
import io.brulejr.iotpipeline.ingest.SensorEnvelope
import io.brulejr.iotpipeline.promote.PromotedDevice
import io.brulejr.iotpipeline.promote.PromotedReading
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReadingParserTest {

    private val jsonMapper = JsonMapper.builder().build()
    private val parser = ReadingParser()
    private val key = DeviceKey("rtl433", "Acurite-Tower/A/3064")
    private val receivedAt = Instant.parse("2026-10-11T08:00:00Z")

    private fun reading(json: String, sensors: List<SensorMapping>): PromotedReading {
        val envelope = SensorEnvelope("rtl433", "rtl_433/pi/events", receivedAt, jsonMapper.readTree(json))
        val model = ModelRecord(
            fingerprint = "model-hash",
            source = "rtl433",
            name = "Acurite-Tower",
            structure = "{}",
            discoveredAt = receivedAt,
            category = "weather",
            sensors = sensors,
        )
        return PromotedReading(
            classification = Classification.Recognised(
                reading = FingerprintedReading(envelope, Fingerprint("e", "d", "m", "{}")),
                deviceKey = key,
                model = model,
            ),
            device = PromotedDevice(key, "Back garden sensor", "thermometer", "garden", receivedAt),
        )
    }

    @Test
    fun `an analog field is read as a number`() {
        val parsed = parser.parse(
            reading(
                """{"temperature_C":26.5}""",
                listOf(SensorMapping("temperature_C", SensorType.ANALOG, "temperature")),
            ),
        )

        val value = assertIs<SensorValue.Analog>(parsed.values.single())
        assertEquals(26.5, value.value)
        assertEquals("temperature", value.mapping.classname)
    }

    @Test
    fun `rtl_433 encodes a state as a number, which reads as a state`() {
        val parsed = parser.parse(
            reading(
                """{"tamper":1}""",
                listOf(SensorMapping("tamper", SensorType.BINARY, "tamper")),
            ),
        )

        assertEquals(true, assertIs<SensorValue.Binary>(parsed.values.single()).on)
    }

    @Test
    fun `a json boolean also reads as a state`() {
        val parsed = parser.parse(
            reading(
                """{"wet":true}""",
                listOf(SensorMapping("wet", SensorType.BINARY, "moisture")),
            ),
        )

        assertEquals(true, assertIs<SensorValue.Binary>(parsed.values.single()).on)
    }

    @Test
    fun `an inverted mapping reports the consumer's sense, not the source's`() {
        // battery_ok = 1 means the battery is healthy; a battery class means on is trouble.
        val parsed = parser.parse(
            reading(
                """{"battery_ok":1}""",
                listOf(SensorMapping("battery_ok", SensorType.BINARY, "battery", inverted = true)),
            ),
        )

        assertEquals(false, assertIs<SensorValue.Binary>(parsed.values.single()).on)
    }

    @Test
    fun `a flat battery reports trouble`() {
        val parsed = parser.parse(
            reading(
                """{"battery_ok":0}""",
                listOf(SensorMapping("battery_ok", SensorType.BINARY, "battery", inverted = true)),
            ),
        )

        assertEquals(true, assertIs<SensorValue.Binary>(parsed.values.single()).on)
    }

    @Test
    fun `every mapping is read, and nothing else in the payload is`() {
        val parsed = parser.parse(
            reading(
                """{"temperature_C":26.5,"humidity":47,"battery_ok":1,"mic":"CHECKSUM","rssi":-0.11}""",
                listOf(
                    SensorMapping("temperature_C", SensorType.ANALOG, "temperature"),
                    SensorMapping("humidity", SensorType.ANALOG, "humidity"),
                    SensorMapping("battery_ok", SensorType.BINARY, "battery", inverted = true),
                ),
            ),
        )

        assertEquals(listOf("temperature", "humidity", "battery"), parsed.values.map { it.mapping.classname })
    }

    @Test
    fun `the device and model travel with the values`() {
        val parsed = parser.parse(
            reading(
                """{"temperature_C":1.0}""",
                listOf(SensorMapping("temperature_C", SensorType.ANALOG, "temperature")),
            ),
        )

        assertEquals("Back garden sensor", parsed.device.name)
        assertEquals("weather", parsed.model.category)
        assertEquals(receivedAt, parsed.receivedAt)
    }

    @Test
    fun `a value that cannot be read is left out rather than guessed`() {
        // Curation refuses this pairing, so reaching the parser means something is wrong
        // upstream; inventing a number would hide it.
        val parsed = parser.parse(
            reading(
                """{"mic":"CHECKSUM","temperature_C":26.5}""",
                listOf(
                    SensorMapping("mic", SensorType.ANALOG, "temperature"),
                    SensorMapping("temperature_C", SensorType.ANALOG, "temperature"),
                ),
            ),
        )

        assertEquals(1, parsed.values.size)
        assertTrue(parsed.values.single() is SensorValue.Analog)
    }

    @Test
    fun `a null field yields no value`() {
        val parsed = parser.parse(
            reading(
                """{"temperature_C":null}""",
                listOf(SensorMapping("temperature_C", SensorType.ANALOG, "temperature")),
            ),
        )

        assertEquals(0, parsed.values.size)
    }
}
