/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.transform.homeassistant

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * A device discovery message: one per device, describing every entity it has.
 *
 * Device-based discovery rather than one message per entity, so a device and its
 * sensors appear in Home Assistant together and are described in one place.
 */
data class HomeAssistantDiscovery(
    val device: HomeAssistantDevice,
    val origin: HomeAssistantOrigin,
    @get:JsonProperty("state_topic") val stateTopic: String,
    val components: Map<String, HomeAssistantComponent>,
)
