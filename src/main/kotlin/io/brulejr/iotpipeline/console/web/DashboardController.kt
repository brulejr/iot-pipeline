/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.console.web

import io.brulejr.iotpipeline.console.ConsoleDatafill
import io.brulejr.iotpipeline.console.ConsoleService
import io.brulejr.iotpipeline.monitor.MonitorService
import io.brulejr.iotpipeline.recommend.RecommendationService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

/**
 * The console's front page: the pipeline's health and throughput, the best promotion
 * candidates, and the devices heard last.
 */
@Controller
class DashboardController(
    private val console: ConsoleService,
    private val recommendations: RecommendationService,
    private val monitor: MonitorService,
    private val datafill: ConsoleDatafill,
) {

    @GetMapping("/")
    fun root() = "redirect:/ui"

    @GetMapping("/ui")
    fun dashboard(model: Model): String {
        overview(model)
        return "ui/dashboard"
    }

    /** The part of the dashboard that reloads itself. */
    @GetMapping("/ui/overview")
    fun overview(model: Model): String {
        model.addAttribute("overview", console.overview())
        // Java collections: SpEL cannot call methods on Kotlin's empty singletons.
        model.addAttribute("candidates", recommendations.recommendations().filter { it.recommended }.take(5).toMutableList())
        model.addAttribute("heard", monitor.heard().take(8).toMutableList())
        model.addAttribute("refreshSeconds", datafill.refreshInterval.toSeconds())
        return "ui/dashboard :: overview"
    }
}
