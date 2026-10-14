/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.transform.homeassistant

import io.brulejr.iotpipeline.classify.DeviceKey
import io.brulejr.iotpipeline.classify.ModelRecord
import io.brulejr.iotpipeline.classify.SensorMapping
import io.brulejr.iotpipeline.classify.SensorType
import io.brulejr.iotpipeline.parse.ParsedReading
import io.brulejr.iotpipeline.parse.SensorValue
import io.brulejr.iotpipeline.promote.PromotedDevice
import io.brulejr.iotpipeline.transform.TransformDatafill
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeAssistantTransformerTest {

    private val jsonMapper = JsonMapper.builder().build()
    private val receivedAt = Instant.parse("2026-10-12T15:00:00Z")

    private val temperature = SensorMapping("temperature_C", SensorType.ANALOG, "temperature", "Temperature")
    private val humidity = SensorMapping("humidity", SensorType.ANALOG, "humidity", "Humidity")
    private val battery = SensorMapping("battery_ok", SensorType.BINARY, "battery", "Battery", inverted = true)

    private fun transformer(datafill: TransformDatafill.HomeAssistant = TransformDatafill.HomeAssistant()) =
        HomeAssistantTransformer(datafill, jsonMapper)

    private fun reading(
        sensors: List<SensorMapping> = listOf(temperature, humidity, battery),
        values: List<SensorValue> = listOf(
            SensorValue.Analog(temperature, 21.2),
            SensorValue.Analog(humidity, 43.0),
            // Already inverted by the parser: a flat battery is trouble, so on.
            SensorValue.Binary(battery, true),
        ),
    ) = ParsedReading(
        device = PromotedDevice(
            DeviceKey("rtl433", "Acurite-Tower/A/3064"), "Back garden sensor", "thermometer", "garden", receivedAt,
        ),
        model = ModelRecord(
            fingerprint = "fp", source = "rtl433", name = "Acurite-Tower", structure = "{}",
            discoveredAt = receivedAt, category = "weather", sensors = sensors,
        ),
        receivedAt = receivedAt,
        values = values,
    )

    private fun json(payload: String): JsonNode = jsonMapper.readTree(payload)

    @Test
    fun `the first reading sends discovery, then state`() {
        val messages = transformer().transform(reading())

        assertEquals(
            listOf("homeassistant/device/rtl433_acurite_tower_a_3064/config", "iot-pipeline/rtl433_acurite_tower_a_3064/state"),
            messages.map { it.topic },
        )
        assertTrue(messages[0].retained, "discovery must be retained")
    }

    @Test
    fun `discovery is not resent while nothing changes`() {
        val transformer = transformer()
        transformer.transform(reading())

        val second = transformer.transform(reading())

        assertEquals(listOf("iot-pipeline/rtl433_acurite_tower_a_3064/state"), second.map { it.topic })
    }

    @Test
    fun `discovery is resent when curation changes what the device reports`() {
        val transformer = transformer()
        transformer.transform(reading())

        val recurated = transformer.transform(
            reading(sensors = listOf(temperature), values = listOf(SensorValue.Analog(temperature, 21.2))),
        )

        assertTrue(recurated.first().topic.endsWith("/config"))
    }

    @Test
    fun `the device's free-text name never reaches a topic`() {
        val messages = transformer().transform(reading())

        assertTrue(messages.none { " " in it.topic || "Back" in it.topic }, messages.map { it.topic }.toString())
    }

    @Test
    fun `discovery describes the device in Home Assistant's own field names`() {
        val discovery = json(transformer().transform(reading()).first().payload)

        assertEquals("Back garden sensor", discovery["device"]["name"].asString())
        assertEquals("garden", discovery["device"]["suggested_area"].asString())
        assertEquals("rtl433_acurite_tower_a_3064", discovery["device"]["identifiers"][0].asString())
        assertEquals("iot-pipeline", discovery["origin"]["name"].asString())
        assertEquals("iot-pipeline/rtl433_acurite_tower_a_3064/state", discovery["state_topic"].asString())
    }

    @Test
    fun `an analog mapping becomes a sensor with its unit and statistics`() {
        val component = json(transformer().transform(reading()).first().payload)["components"]["temperature_c"]

        assertEquals("sensor", component["platform"].asString())
        assertEquals("temperature", component["device_class"].asString())
        assertEquals("°C", component["unit_of_measurement"].asString())
        assertEquals("measurement", component["state_class"].asString())
        assertEquals("{{ value_json.temperature_C }}", component["value_template"].asString())
        assertEquals("rtl433_acurite_tower_a_3064_temperature_c", component["unique_id"].asString())
        assertNull(component["payload_on"], "a sensor has no on/off payloads")
    }

    @Test
    fun `a binary mapping becomes a binary sensor with plain on and off`() {
        val component = json(transformer().transform(reading()).first().payload)["components"]["battery_ok"]

        assertEquals("binary_sensor", component["platform"].asString())
        assertEquals("battery", component["device_class"].asString())
        // No per-field inversion here: the parser already applied it.
        assertEquals("ON", component["payload_on"].asString())
        assertEquals("OFF", component["payload_off"].asString())
        assertNull(component["unit_of_measurement"])
    }

    @Test
    fun `state carries each value under its field name`() {
        val state = json(transformer().transform(reading()).last().payload)

        assertEquals(21.2, state["temperature_C"].asDouble())
        assertEquals(43.0, state["humidity"].asDouble())
        assertEquals("ON", state["battery_ok"].asString())
        assertEquals("2026-10-12T15:00:00Z", state["received_at"].asString())
    }

    @Test
    fun `a healthy battery reports off`() {
        val state = json(
            transformer().transform(
                reading(values = listOf(SensorValue.Binary(battery, false))),
            ).last().payload,
        )

        assertEquals("OFF", state["battery_ok"].asString())
    }

    @Test
    fun `state retention follows configuration, discovery is always retained`() {
        val messages = transformer(TransformDatafill.HomeAssistant(retainState = false)).transform(reading())

        assertTrue(messages.first().retained)
        assertFalse(messages.last().retained)
    }

    @Test
    fun `prefixes are configurable`() {
        val messages = transformer(
            TransformDatafill.HomeAssistant(discoveryPrefix = "ha", statePrefix = "house/sensors"),
        ).transform(reading())

        assertEquals(
            listOf("ha/device/rtl433_acurite_tower_a_3064/config", "house/sensors/rtl433_acurite_tower_a_3064/state"),
            messages.map { it.topic },
        )
    }

    @Test
    fun `a reading with no values sends nothing`() {
        assertTrue(transformer().transform(reading(values = emptyList())).isEmpty())
    }

    private val key = DeviceKey("rtl433", "Acurite-Tower/A/3064")

    @Test
    fun `a demoted device has its discovery and retained state cleared`() {
        val messages = transformer().remove(key)

        assertEquals(
            listOf(
                "homeassistant/device/rtl433_acurite_tower_a_3064/config",
                "iot-pipeline/rtl433_acurite_tower_a_3064/state",
            ),
            messages.map { it.topic },
        )
        // An empty retained message is what deletes a retained one, and the device with it.
        assertTrue(messages.all { it.payload.isEmpty() && it.retained })
    }

    @Test
    fun `state that is not retained needs no clearing`() {
        val messages = transformer(TransformDatafill.HomeAssistant(retainState = false)).remove(key)

        assertEquals(listOf("homeassistant/device/rtl433_acurite_tower_a_3064/config"), messages.map { it.topic })
    }

    @Test
    fun `a device promoted again after demotion is rediscovered`() {
        val transformer = transformer()
        transformer.transform(reading())
        transformer.remove(key)

        val messages = transformer.transform(reading())

        assertEquals("homeassistant/device/rtl433_acurite_tower_a_3064/config", messages.first().topic)
        assertTrue(messages.first().payload.isNotEmpty())
    }
}
