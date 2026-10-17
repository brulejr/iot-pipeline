/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote.api

import io.brulejr.iotpipeline.classify.DeviceKey

/**
 * One entry in a bulk promotion: a device and what an operator says about it.
 *
 * Deliberately the same shape as an entry in `GET /api/promoted-devices` and in
 * `promotion-seed.json`, so either can be sent back as is. Extra fields, such as
 * `promotedAt`, are ignored.
 */
data class BulkPromotionRequest(
    val deviceKey: DeviceKey,
    val name: String,
    val type: String,
    val area: String,
)
