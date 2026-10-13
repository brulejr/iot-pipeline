/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.fingerprint.api

import io.brulejr.iotpipeline.fingerprint.Fingerprint
import io.brulejr.iotpipeline.fingerprint.FingerprintService
import io.brulejr.iotpipeline.ingest.SensorEnvelope
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import java.time.Instant

/**
 * Fingerprints a payload without sending it down the pipeline, to see which model a
 * reading would be matched to, or why two readings are or are not treated as one.
 */
@RestController
@RequestMapping("/api/fingerprints")
class FingerprintController(private val service: FingerprintService) {

    /** [payload] is a reading as the source sends it, such as one rtl_433 event. */
    @PostMapping
    fun fingerprint(
        @RequestBody payload: JsonNode,
        @RequestParam(defaultValue = "rtl433") source: String,
    ): Fingerprint = service.fingerprint(SensorEnvelope(source, "api", Instant.now(), payload))
}
