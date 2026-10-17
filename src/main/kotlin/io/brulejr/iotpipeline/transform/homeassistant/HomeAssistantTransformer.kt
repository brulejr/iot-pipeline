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
import io.brulejr.iotpipeline.classify.SensorType
import io.brulejr.iotpipeline.parse.ParsedReading
import io.brulejr.iotpipeline.parse.SensorValue
import io.brulejr.iotpipeline.transform.OutboundMessage
import io.brulejr.iotpipeline.transform.ReadingTransformerPort
import io.brulejr.iotpipeline.transform.TransformDatafill
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.ConcurrentHashMap

/**
 * Shapes readings for Home Assistant's MQTT device discovery: a retained discovery
 * message describing each device and its entities, and a state message per reading.
 *
 * Discovery is sent only when it differs from the last one sent for that device: once
 * after this application starts, and again when a curation changes what the device
 * reports. Sending it with every reading, as the prototype did, doubles the traffic for
 * nothing Home Assistant does not already know. If a sent discovery never arrives, it is
 * not resent until something changes; it is retained, so the broker keeps the last one
 * that did.
 *
 * A demoted device is withdrawn by clearing what the broker retains for it: an empty
 * retained discovery is how Home Assistant is told to delete a device, and an empty
 * retained state stops the broker handing its last reading to anyone who subscribes.
 * The device's last discovery is forgotten too, so promoting it again recreates it.
 *
 * Topics and ids come from the device key, never the device's name, which is free text
 * a person chose: Home Assistant ids are restricted to letters, digits, `_` and `-`.
 */
class HomeAssistantTransformer(
    private val datafill: TransformDatafill.HomeAssistant,
    private val jsonMapper: JsonMapper,
) : ReadingTransformerPort {

    private val lastDiscovery = ConcurrentHashMap<String, String>()

    override fun transform(reading: ParsedReading): List<OutboundMessage> {
        if (reading.values.isEmpty()) return emptyList()
        val id = idOf(reading.device.deviceKey)
        val stateTopic = stateTopicOf(id)
        val discovery = jsonMapper.writeValueAsString(discoveryOf(reading, id, stateTopic))

        return buildList {
            // Discovery first: Home Assistant ignores state for an entity it does not know.
            if (lastDiscovery.put(id, discovery) != discovery) {
                add(OutboundMessage(discoveryTopicOf(id), discovery, retained = true))
            }
            add(OutboundMessage(stateTopic, jsonMapper.writeValueAsString(stateOf(reading)), datafill.retainState))
        }
    }

    override fun remove(deviceKey: DeviceKey): List<OutboundMessage> {
        val id = idOf(deviceKey)
        lastDiscovery.remove(id)
        return buildList {
            // Discovery first, so Home Assistant drops the entities before their state goes.
            add(OutboundMessage(discoveryTopicOf(id), "", retained = true))
            // Only retained state lingers on the broker; anything else is already gone.
            if (datafill.retainState) add(OutboundMessage(stateTopicOf(id), "", retained = true))
        }
    }

    private fun discoveryTopicOf(id: String) = "${datafill.discoveryPrefix}/device/$id/config"

    private fun stateTopicOf(id: String) = "${datafill.statePrefix}/$id/state"

    private fun discoveryOf(reading: ParsedReading, id: String, stateTopic: String) = HomeAssistantDiscovery(
        device = HomeAssistantDevice(
            identifiers = listOf(id),
            name = reading.device.name,
            model = reading.model.name,
            suggestedArea = reading.device.area,
        ),
        origin = HomeAssistantOrigin(datafill.originName),
        stateTopic = stateTopic,
        // Every mapping becomes an entity, read or not this time, so a reading missing
        // one value does not make Home Assistant drop the entity.
        components = reading.model.sensors.associate { mapping ->
            val componentId = idOf(mapping.name)
            componentId to when (mapping.type) {
                SensorType.ANALOG -> HomeAssistantComponent(
                    platform = "sensor",
                    name = mapping.friendlyName ?: mapping.classname,
                    uniqueId = "${id}_$componentId",
                    deviceClass = mapping.classname,
                    valueTemplate = "{{ value_json.${mapping.name} }}",
                    unitOfMeasurement = homeAssistantUnitFor(mapping.name),
                    stateClass = "measurement",
                )
                SensorType.BINARY -> HomeAssistantComponent(
                    platform = "binary_sensor",
                    name = mapping.friendlyName ?: mapping.classname,
                    uniqueId = "${id}_$componentId",
                    deviceClass = homeAssistantBinaryClassFor(mapping.classname, reading.device.type),
                    valueTemplate = "{{ value_json.${mapping.name} }}",
                    payloadOn = ON,
                    payloadOff = OFF,
                )
            }
        },
    )

    /**
     * Keyed by payload field rather than class: a device with two temperature probes
     * has two `temperature` classes, but its field names are unique.
     */
    private fun stateOf(reading: ParsedReading): Map<String, Any> = buildMap {
        reading.values.forEach { value ->
            put(
                value.mapping.name,
                when (value) {
                    is SensorValue.Analog -> value.value
                    // Inversion was applied when the payload was parsed.
                    is SensorValue.Binary -> if (value.on) ON else OFF
                },
            )
        }
        put("received_at", reading.receivedAt.toString())
    }

    private companion object {
        const val ON = "ON"
        const val OFF = "OFF"

        fun idOf(deviceKey: DeviceKey) = idOf("${deviceKey.source}_${deviceKey.id}")

        /** Lower case, with every run of anything else collapsed to one underscore. */
        fun idOf(text: String) = text.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    }
}
