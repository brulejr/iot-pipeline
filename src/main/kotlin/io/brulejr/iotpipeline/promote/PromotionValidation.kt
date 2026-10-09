/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote

/**
 * Checks a promotion and returns the result, writing nothing. Shared by every
 * [PromotionPort] so the rule cannot differ between implementations.
 */
fun validatePromotion(device: PromotedDevice): PromotionResult.Invalid? {
    val problems = buildList {
        if (device.deviceKey.source.isBlank()) add(PromotionProblem("source", "must not be blank"))
        if (device.deviceKey.id.isBlank()) add(PromotionProblem("deviceId", "must not be blank"))
        if (device.name.isBlank()) add(PromotionProblem("name", "must not be blank"))
        if (device.type.isBlank()) add(PromotionProblem("type", "must not be blank"))
        if (device.area.isBlank()) add(PromotionProblem("area", "must not be blank"))
    }
    return if (problems.isEmpty()) null else PromotionResult.Invalid(problems)
}
