/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.fingerprint

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Lower-case hex SHA-256 of [input]. Every fingerprint in the pipeline is one of these. */
fun sha256Hex(input: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
