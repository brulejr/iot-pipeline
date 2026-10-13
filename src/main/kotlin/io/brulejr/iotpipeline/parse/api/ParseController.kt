/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.parse.api

import io.brulejr.iotpipeline.parse.ParseService
import io.brulejr.iotpipeline.parse.SensorValue
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

/**
 * Parses a payload under a model's current curation without storing anything, to check
 * a curation against a real reading before relying on it.
 */
@RestController
@RequestMapping("/api/models/{fingerprint}/parse")
class ParseController(private val service: ParseService) {

    @PostMapping
    fun preview(
        @PathVariable fingerprint: String,
        @RequestBody payload: JsonNode,
    ): ResponseEntity<List<SensorValue>> = service.preview(fingerprint, payload)
        ?.let { ResponseEntity.ok(it) }
        ?: ResponseEntity.notFound().build()
}
