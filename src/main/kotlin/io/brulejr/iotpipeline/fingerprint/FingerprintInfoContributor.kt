/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.fingerprint

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** Which fields fingerprinting ignores, since that decides what counts as one model or one reading. */
class FingerprintInfoContributor(private val datafill: FingerprintDatafill) : InfoContributor {

    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "fingerprint",
            mapOf(
                "enabled" to datafill.enabled,
                "excludedModelFields" to datafill.excludedModelFields.sorted(),
                "excludedEventFields" to datafill.excludedEventFields.sorted(),
            ),
        )
    }
}
