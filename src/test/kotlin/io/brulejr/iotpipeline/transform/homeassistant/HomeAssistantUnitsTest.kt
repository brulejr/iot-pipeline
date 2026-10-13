/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.transform.homeassistant

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeAssistantUnitsTest {

    @Test
    fun `temperature carries its scale in the field name`() {
        assertEquals("°C", homeAssistantUnitFor("temperature_C"))
        // The prototype reported this as Celsius.
        assertEquals("°F", homeAssistantUnitFor("temperature_F"))
    }

    @Test
    fun `the most specific suffix wins`() {
        assertEquals("mm/h", homeAssistantUnitFor("rain_rate_mm_h"))
        assertEquals("mm", homeAssistantUnitFor("rain_mm"))
        assertEquals("km/h", homeAssistantUnitFor("wind_avg_km_h"))
        assertEquals("mV", homeAssistantUnitFor("battery_mV"))
    }

    @Test
    fun `percentages that rtl_433 leaves unlabelled`() {
        assertEquals("%", homeAssistantUnitFor("humidity"))
        assertEquals("%", homeAssistantUnitFor("moisture"))
        assertEquals("%", homeAssistantUnitFor("humidity_2"))
    }

    @Test
    fun `a name carrying no unit gets none`() {
        assertNull(homeAssistantUnitFor("closed"))
        assertNull(homeAssistantUnitFor("tamper"))
        assertNull(homeAssistantUnitFor("battery_ok"))
    }
}
