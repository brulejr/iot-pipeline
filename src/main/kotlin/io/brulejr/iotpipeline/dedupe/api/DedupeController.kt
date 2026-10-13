/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.dedupe.api

import io.brulejr.iotpipeline.dedupe.DedupeService
import io.brulejr.iotpipeline.dedupe.DedupeStats
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** What dedupe has passed and discarded since startup. */
@RestController
@RequestMapping("/api/dedupe")
class DedupeController(private val service: DedupeService) {

    @GetMapping
    fun stats(): DedupeStats = service.stats()
}
