/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote

import io.brulejr.iotpipeline.classify.Classification
import io.brulejr.iotpipeline.classify.DeviceKey
import java.util.concurrent.atomic.AtomicLong

/**
 * Stage 3: which devices are approved for normal processing, and the gate that applies
 * those approvals to every classified reading.
 *
 * Promotion is the manual review step the design calls for: the recommendation engine
 * suggests candidates, but nothing is promoted without someone saying so.
 */
class PromotionService(private val registry: PromotionPort) {

    private val promoted = AtomicLong()
    private val promotedButUnreadable = AtomicLong()
    private val unpromoted = AtomicLong()

    /** The promotion for this device, or null if it has not been promoted. */
    fun find(deviceKey: DeviceKey): PromotedDevice? = registry.find(deviceKey)

    /** Every promoted device, oldest promotion first. */
    fun all(): List<PromotedDevice> = registry.all()

    /**
     * Promotes a device, replacing any earlier promotion of it. Nothing is written when
     * the promotion is invalid.
     */
    fun promote(device: PromotedDevice): PromotionResult {
        validate(device)?.let { return it }
        registry.save(device)
        return PromotionResult.Promoted(device)
    }

    /** Withdraws a promotion. Returns true if the device had been promoted. */
    fun demote(deviceKey: DeviceKey): Boolean = registry.demote(deviceKey)

    /** Every problem with a promotion, or null if it can be stored. */
    fun validate(device: PromotedDevice): PromotionResult.Invalid? {
        val problems = buildList {
            if (device.deviceKey.source.isBlank()) add(PromotionProblem("source", "must not be blank"))
            if (device.deviceKey.id.isBlank()) add(PromotionProblem("deviceId", "must not be blank"))
            if (device.name.isBlank()) add(PromotionProblem("name", "must not be blank"))
            if (device.type.isBlank()) add(PromotionProblem("type", "must not be blank"))
            if (device.area.isBlank()) add(PromotionProblem("area", "must not be blank"))
        }
        return if (problems.isEmpty()) null else PromotionResult.Invalid(problems)
    }

    /** Where the gate sends a classified reading. */
    fun decide(classification: Classification): PromotionDecision {
        val approved = classification.deviceKey?.let { registry.find(it) } != null
        return when {
            !approved -> PromotionDecision.UNPROMOTED.also { unpromoted.incrementAndGet() }
            !classification.model.recognised ->
                PromotionDecision.PROMOTED_BUT_UNREADABLE.also { promotedButUnreadable.incrementAndGet() }
            else -> PromotionDecision.PROMOTED.also { promoted.incrementAndGet() }
        }
    }

    /**
     * Attaches the promotion to a reading the gate passed, which later stages need
     * alongside the model.
     */
    fun promoted(classification: Classification): PromotedReading {
        val deviceKey = requireNotNull(classification.deviceKey) { "a promoted reading has a device key" }
        val device = checkNotNull(registry.find(deviceKey)) { "${deviceKey.id} was demoted after the gate passed it" }
        return PromotedReading(classification, device)
    }

    fun stats() = PromotionStats(promoted.get(), promotedButUnreadable.get(), unpromoted.get())
}
