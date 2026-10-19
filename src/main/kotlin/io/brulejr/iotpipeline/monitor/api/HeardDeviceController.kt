/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.monitor.api

import io.brulejr.iotpipeline.monitor.HeardDevice
import io.brulejr.iotpipeline.monitor.MonitorService
import io.brulejr.iotpipeline.monitor.MonitorStats
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The devices heard recently, promoted or not, each with the last payload it sent, and
 * what the live monitor has done. The stream itself is a console page, `/ui/monitor`.
 */
@RestController
@RequestMapping("/api/monitor")
class HeardDeviceController(private val service: MonitorService) {

    @GetMapping
    fun stats(): MonitorStats = service.stats()

    /** Most recently heard first. */
    @GetMapping("/heard")
    fun heard(): List<HeardDevice> = service.heard()
}
