/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.transform

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @property enabled when false, stored readings are not transformed at all.
 * @property homeAssistant how readings are shaped for Home Assistant's MQTT discovery.
 */
@ConfigurationProperties("pipeline.transform")
data class TransformDatafill(
    val enabled: Boolean = true,
    val homeAssistant: HomeAssistant = HomeAssistant(),
) {
    /**
     * @property discoveryPrefix the topic root Home Assistant watches for discovery.
     *   `homeassistant` unless its MQTT integration has been reconfigured.
     * @property statePrefix the topic root for each device's readings.
     * @property originName how this application names itself to Home Assistant, which
     *   requires discovery to say where it came from.
     * @property retainState whether readings are retained, so Home Assistant shows the
     *   last value straight after it restarts rather than "unknown" until the device next
     *   reports, which for a door contact can be an hour.
     */
    data class HomeAssistant(
        val discoveryPrefix: String = "homeassistant",
        val statePrefix: String = "iot-pipeline",
        val originName: String = "iot-pipeline",
        val retainState: Boolean = true,
    ) {
        init {
            listOf("discovery-prefix" to discoveryPrefix, "state-prefix" to statePrefix).forEach { (name, value) ->
                require(value.isNotBlank()) { "pipeline.transform.home-assistant.$name must not be blank" }
                // A wildcard would make every device's topic collide, and an edge slash
                // would produce an empty topic level.
                require(value.none { it in "+#" } && !value.startsWith("/") && !value.endsWith("/")) {
                    "pipeline.transform.home-assistant.$name must be a plain MQTT topic prefix: " +
                        "no wildcards, and no leading or trailing slash"
                }
            }
            require(originName.isNotBlank()) { "pipeline.transform.home-assistant.origin-name must not be blank" }
        }
    }
}
