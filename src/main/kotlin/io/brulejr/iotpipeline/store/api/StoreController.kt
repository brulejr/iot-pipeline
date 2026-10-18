/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.store.api

import io.brulejr.iotpipeline.store.StoreService
import io.brulejr.iotpipeline.store.StoreStats
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * What has been stored since startup. Readings themselves are queried from PostgreSQL,
 * by Grafana or psql, not through this application.
 */
@RestController
@RequestMapping("/api/store")
class StoreController(private val service: StoreService) {

    @GetMapping
    fun stats(): StoreStats = service.stats()
}
