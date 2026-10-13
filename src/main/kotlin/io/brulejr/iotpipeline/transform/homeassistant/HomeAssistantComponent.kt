/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.transform.homeassistant

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * One entity within a device's discovery message.
 *
 * @property platform `sensor` for a measurement, `binary_sensor` for an on/off state.
 * @property stateClass `measurement` for an analog reading, which is what lets Home
 *   Assistant keep long-term statistics for it.
 * @property payloadOn the state value meaning on. The pipeline has already applied
 *   any inversion, so this is always `ON`.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class HomeAssistantComponent(
    val platform: String,
    val name: String,
    @get:JsonProperty("unique_id") val uniqueId: String,
    @get:JsonProperty("device_class") val deviceClass: String,
    @get:JsonProperty("value_template") val valueTemplate: String,
    @get:JsonProperty("unit_of_measurement") val unitOfMeasurement: String? = null,
    @get:JsonProperty("state_class") val stateClass: String? = null,
    @get:JsonProperty("payload_on") val payloadOn: String? = null,
    @get:JsonProperty("payload_off") val payloadOff: String? = null,
)
