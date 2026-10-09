/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify.api

import io.brulejr.iotpipeline.classify.CurationResult
import io.brulejr.iotpipeline.classify.ModelRecord
import io.brulejr.iotpipeline.classify.ModelRegistryPort
import io.brulejr.iotpipeline.classify.SensorMapping
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Reads the model catalogue and curates it.
 *
 * Models discover themselves from live traffic, but a model only becomes recognised
 * when someone supplies its sensor mappings, and this is how. Without it the registry
 * would fill with structures that could never be acted on.
 */
@RestController
@RequestMapping("/api/models")
class ModelController(private val registry: ModelRegistryPort) {

    /** Every model seen, oldest discovery first. */
    @GetMapping
    fun list(): List<ModelRecord> = registry.all()

    @GetMapping("/{fingerprint}")
    fun get(@PathVariable fingerprint: String): ResponseEntity<ModelRecord> =
        registry.find(fingerprint)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    /**
     * Replaces a model's sensor mappings. An empty list makes the model unrecognised
     * again, which is the way to undo a curation.
     *
     * A mapping naming a field the model's structure does not have is rejected whole,
     * rather than stored to fail quietly in a later stage.
     */
    @PutMapping("/{fingerprint}/sensors")
    fun curate(
        @PathVariable fingerprint: String,
        @RequestBody request: SensorsUpdateRequest,
    ): ResponseEntity<Any> = when (val result = registry.curate(fingerprint, request.sensors)) {
        is CurationResult.Curated -> ResponseEntity.ok(result.model)
        is CurationResult.ModelNotFound -> ResponseEntity.notFound().build()
        is CurationResult.UnknownFields -> ResponseEntity.badRequest().body(
            UnknownFieldsError(
                message = "Not in this model's structure: " +
                    result.fields.sorted().joinToString(", "),
                unknownFields = result.fields.sorted(),
            ),
        )
    }
}

/** Body of a curation request. */
data class SensorsUpdateRequest(val sensors: List<SensorMapping> = emptyList())

/** Returned with 400 when a mapping names a field the model does not have. */
data class UnknownFieldsError(val message: String, val unknownFields: List<String>)
