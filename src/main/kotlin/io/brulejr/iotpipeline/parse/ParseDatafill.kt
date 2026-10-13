/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.parse

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @property dropIncomplete when true, a reading for which some sensor mapping yields no
 *   value is logged and dropped instead of stored with the values it has. Off by default:
 *   a partial reading is still a true one, and curation makes it rare.
 */
@ConfigurationProperties("pipeline.parse")
data class ParseDatafill(
    val dropIncomplete: Boolean = false,
)
