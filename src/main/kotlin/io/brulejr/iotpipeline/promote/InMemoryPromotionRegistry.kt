/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote

import io.brulejr.iotpipeline.classify.DeviceKey
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory [PromotionPort], for running without a database: the tests select it with
 * `pipeline.promotion-registry.type=memory`.
 *
 * Promotions do not survive a restart, so every device reverts to unpromoted. Use the
 * MongoDB implementation to keep them.
 */
class InMemoryPromotionRegistry : PromotionPort {

    private val byKey = ConcurrentHashMap<DeviceKey, PromotedDevice>()

    override fun find(deviceKey: DeviceKey): PromotedDevice? = byKey[deviceKey]

    override fun all(): List<PromotedDevice> = byKey.values.sortedBy { it.promotedAt }

    override fun promote(device: PromotedDevice): PromotionResult {
        validatePromotion(device)?.let { return it }
        byKey[device.deviceKey] = device
        return PromotionResult.Promoted(device)
    }

    override fun demote(deviceKey: DeviceKey): Boolean = byKey.remove(deviceKey) != null
}
