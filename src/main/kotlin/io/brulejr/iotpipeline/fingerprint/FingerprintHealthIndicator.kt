/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.fingerprint

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

/**
 * Always up: fingerprinting is pure computation with nothing to fail. Reports how many
 * readings have been fingerprinted, which shows whether anything is arriving at all.
 */
class FingerprintHealthIndicator(private val service: FingerprintService) : HealthIndicator {

    override fun health(): Health = Health.up()
        .withDetail("fingerprinted", service.fingerprintedCount())
        .build()
}
