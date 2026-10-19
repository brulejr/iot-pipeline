/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.recommend.web

import io.brulejr.iotpipeline.recommend.RecommendationDatafill
import io.brulejr.iotpipeline.recommend.RecommendationService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * The console's recommendation page: unpromoted devices, best candidate first, each a
 * click away from being watched live or promoted.
 */
@Controller
@RequestMapping("/ui/recommendations")
class RecommendationPageController(
    private val recommendations: RecommendationService,
    private val datafill: RecommendationDatafill,
) {

    @GetMapping
    fun list(@RequestParam(defaultValue = "false") all: Boolean, model: Model): String {
        rows(all, model)
        model.addAttribute("datafill", datafill)
        return "ui/recommendations/list"
    }

    /** The rows alone, reloaded on a timer and after a promotion. */
    @GetMapping("/rows")
    fun rows(@RequestParam(defaultValue = "false") all: Boolean, model: Model): String {
        val candidates = recommendations.recommendations()
        model.addAttribute("candidates", (if (all) candidates else candidates.filter { it.recommended }).toMutableList())
        model.addAttribute("all", all)
        model.addAttribute("recommendedCount", candidates.count { it.recommended })
        model.addAttribute("heardCount", candidates.size)
        return "ui/recommendations/list :: rows"
    }
}
