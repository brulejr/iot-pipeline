/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote.seed

import io.brulejr.iotpipeline.classify.DeviceKey

/**
 * One approval as a seed file records it.
 *
 * Deliberately the same shape as an entry in `GET /api/promoted-devices`, so that
 * response can be saved and fed straight back in. Extra fields in the file, such as the
 * original `promotedAt`, are ignored: a restore records when it happened, not when the
 * approval was first given.
 */
data class PromotionSeed(
    val deviceKey: DeviceKey,
    val name: String,
    val type: String,
    val area: String,
)
