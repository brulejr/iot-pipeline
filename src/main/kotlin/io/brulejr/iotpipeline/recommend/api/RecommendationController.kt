/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.recommend.api

import io.brulejr.iotpipeline.recommend.Recommendation
import io.brulejr.iotpipeline.recommend.RecommendationService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Lists unpromoted devices, best candidate first.
 *
 * Every device heard in the window is listed, with [Recommendation.recommended] marking
 * those heard often enough to suggest; the rest are there so the ranking can be judged.
 * Promote one through `PUT /api/promoted-devices/{source}/{deviceId}`, using its key.
 */
@RestController
@RequestMapping("/api/recommendations")
class RecommendationController(private val service: RecommendationService) {

    @GetMapping
    fun list(): List<Recommendation> = service.recommendations()
}
