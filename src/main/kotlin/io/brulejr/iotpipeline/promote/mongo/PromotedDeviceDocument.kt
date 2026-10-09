/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote.mongo

import io.brulejr.iotpipeline.classify.DeviceKey
import io.brulejr.iotpipeline.promote.PromotedDevice
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * Stored shape of a [PromotedDevice], kept apart from the domain type so Spring Data
 * annotations stay out of the promotion package.
 *
 * The document id is the device key flattened to one string, so the store cannot hold
 * two promotions of one device. Source and id are kept as fields too, because the id
 * alone cannot be split back apart: an rtl_433 device id contains slashes of its own.
 */
@Document("promoted-devices")
data class PromotedDeviceDocument(
    @Id val id: String,
    val source: String,
    val deviceId: String,
    val name: String,
    val type: String,
    val area: String,
    val promotedAt: Instant,
) {
    fun toDevice() = PromotedDevice(
        deviceKey = DeviceKey(source, deviceId),
        name = name,
        type = type,
        area = area,
        promotedAt = promotedAt,
    )

    companion object {
        fun idOf(deviceKey: DeviceKey) = "${deviceKey.source}/${deviceKey.id}"

        fun of(device: PromotedDevice) = PromotedDeviceDocument(
            id = idOf(device.deviceKey),
            source = device.deviceKey.source,
            deviceId = device.deviceKey.id,
            name = device.name,
            type = device.type,
            area = device.area,
            promotedAt = device.promotedAt,
        )
    }
}
