/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.transform.homeassistant

/**
 * The unit Home Assistant expects for a reading, read from rtl_433's field name.
 *
 * rtl_433 encodes a reading's unit in its field name (`temperature_C`, `pressure_hPa`,
 * `wind_avg_km_h`), so the unit is known without anyone curating it. Home Assistant
 * checks a sensor's unit against its device class, so getting it wrong is not cosmetic:
 * reporting `temperature_F` as degrees Celsius would misstate every reading.
 *
 * Returns null where the name carries no unit; Home Assistant then shows a bare number.
 */
fun homeAssistantUnitFor(field: String): String? {
    // Most specific first: `_mm_h` must win over `_mm`.
    SUFFIX_UNITS.firstOrNull { (suffix, _) -> field.endsWith(suffix) }?.let { return it.second }
    return UNITLESS_PERCENT.firstOrNull { field == it || field.startsWith("${it}_") }?.let { "%" }
}

private val SUFFIX_UNITS = listOf(
    "_mm_h" to "mm/h",
    "_in_h" to "in/h",
    "_km_h" to "km/h",
    "_mi_h" to "mph",
    "_m_s" to "m/s",
    "_hPa" to "hPa",
    "_kPa" to "kPa",
    "_PSI" to "psi",
    "_kWh" to "kWh",
    "_ppm" to "ppm",
    "_lux" to "lx",
    "_deg" to "°",
    "_mm" to "mm",
    "_cm" to "cm",
    "_in" to "in",
    "_mV" to "mV",
    "_dB" to "dB",
    "_C" to "°C",
    "_F" to "°F",
    "_W" to "W",
    "_A" to "A",
    "_V" to "V",
)

/**
 * Fields rtl_433 reports as a percentage without saying so in the name, alone or
 * numbered (`humidity_2`). Not `battery`: rtl_433's `battery_ok` is a 0/1 flag and its
 * battery voltage is `battery_mV`, which the suffix rules already cover.
 */
private val UNITLESS_PERCENT = listOf("humidity", "moisture")
