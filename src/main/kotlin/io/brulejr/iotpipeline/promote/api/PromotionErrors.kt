/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote.api

import io.brulejr.iotpipeline.promote.PromotionProblem

/** Returned with 400 when a promotion request is refused. */
data class PromotionErrors(val message: String, val problems: List<PromotionProblem>)
