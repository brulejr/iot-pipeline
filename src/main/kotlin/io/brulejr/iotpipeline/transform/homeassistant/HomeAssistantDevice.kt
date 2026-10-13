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

/** The `device` block of a discovery message: how the device appears in Home Assistant. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class HomeAssistantDevice(
    val identifiers: List<String>,
    val name: String,
    val model: String?,
    @get:JsonProperty("suggested_area") val suggestedArea: String?,
)
