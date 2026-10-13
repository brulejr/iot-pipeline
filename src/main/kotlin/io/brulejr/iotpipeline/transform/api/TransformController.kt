/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.transform.api

import io.brulejr.iotpipeline.transform.OutboundMessage
import io.brulejr.iotpipeline.transform.TransformService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The latest message produced for each topic, to see what a consumer would receive
 * before anything is published.
 */
@RestController
@RequestMapping("/api/outbound-messages")
class TransformController(private val service: TransformService) {

    @GetMapping
    fun latest(): List<OutboundMessage> = service.latest()
}
