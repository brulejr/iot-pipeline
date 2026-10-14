/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.publish.api

import io.brulejr.iotpipeline.publish.PublishService
import io.brulejr.iotpipeline.publish.PublishStats
import io.brulejr.iotpipeline.transform.OutboundMessage
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** What has been published, what is held for retry, and a way to retry now. */
@RestController
@RequestMapping("/api/publish")
class PublishController(private val service: PublishService) {

    @GetMapping
    fun stats(): PublishStats = service.stats()

    /** The messages waiting for the broker, in the order they will be tried. */
    @GetMapping("/pending")
    fun pending(): List<OutboundMessage> = service.held()

    /** Retries held messages now rather than after the backoff, as after fixing the broker. */
    @PostMapping("/flush")
    fun flush(): PublishStats {
        service.flush()
        return service.stats()
    }
}
