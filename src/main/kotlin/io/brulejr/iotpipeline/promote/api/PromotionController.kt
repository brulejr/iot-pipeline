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
import io.brulejr.iotpipeline.promote.PromotedDevice
import io.brulejr.iotpipeline.promote.PromotionPort
import io.brulejr.iotpipeline.promote.PromotionResult
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * Promotes devices and lists the promoted ones.
 *
 * This is the manual review step: the recommendation engine will propose candidates,
 * but a device only passes the gate once someone puts it here.
 *
 * A device is addressed by its source and id — the two halves of the key printed on
 * every `pipeline.classify` line, e.g. `rtl433` and `Acurite-Tower/A/3064`. The id
 * contains slashes, so it is the trailing part of the path.
 */
@RestController
@RequestMapping("/api/promoted-devices")
class PromotionController(private val promotions: PromotionPort) {

    @GetMapping
    fun list(): List<PromotedDevice> = promotions.all()

    @GetMapping("/{source}/{*deviceId}")
    fun get(
        @PathVariable source: String,
        @PathVariable deviceId: String,
    ): ResponseEntity<PromotedDevice> = promotions.find(keyOf(source, deviceId))
        ?.let { ResponseEntity.ok(it) }
        ?: ResponseEntity.notFound().build()

    @PutMapping("/{source}/{*deviceId}")
    fun promote(
        @PathVariable source: String,
        @PathVariable deviceId: String,
        @RequestBody request: PromotionRequest,
    ): ResponseEntity<Any> {
        val device = PromotedDevice(
            deviceKey = keyOf(source, deviceId),
            name = request.name,
            type = request.type,
            area = request.area,
            promotedAt = Instant.now(),
        )
        return when (val result = promotions.promote(device)) {
            is PromotionResult.Promoted -> ResponseEntity.ok(result.device)
            is PromotionResult.Invalid -> ResponseEntity.badRequest().body(
                PromotionErrors(
                    message = result.problems.joinToString("; ") { "${it.field}: ${it.reason}" },
                    problems = result.problems,
                ),
            )
        }
    }

    @DeleteMapping("/{source}/{*deviceId}")
    fun demote(
        @PathVariable source: String,
        @PathVariable deviceId: String,
    ): ResponseEntity<Void> = if (promotions.demote(keyOf(source, deviceId))) {
        ResponseEntity.noContent().build()
    } else {
        ResponseEntity.notFound().build()
    }

    // Spring hands a trailing path match back with its leading slash.
    private fun keyOf(source: String, deviceId: String) = DeviceKey(source, deviceId.removePrefix("/"))
}
