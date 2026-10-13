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

/**
 * The devices approved for normal processing.
 *
 * Promotion is the manual review step the design calls for: the recommendation engine
 * will suggest candidates, but nothing is promoted without someone saying so.
 */
interface PromotionPort {

    /** The promotion for this device, or null if it has not been promoted. */
    fun find(deviceKey: DeviceKey): PromotedDevice?

    /** Every promoted device, oldest promotion first. */
    fun all(): List<PromotedDevice>

    /**
     * Stores a promotion, replacing any earlier one for the same device. Storage only:
     * [PromotionService] decides whether the promotion is valid.
     */
    fun save(device: PromotedDevice)

    /** Withdraws a promotion. Returns true if the device had been promoted. */
    fun demote(deviceKey: DeviceKey): Boolean
}
