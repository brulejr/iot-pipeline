/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.parse

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** Whether incomplete readings are stored or dropped. */
class ParseInfoContributor(private val datafill: ParseDatafill) : InfoContributor {

    override fun contribute(builder: Info.Builder) {
        builder.withDetail("parse", mapOf("dropIncomplete" to datafill.dropIncomplete))
    }
}
