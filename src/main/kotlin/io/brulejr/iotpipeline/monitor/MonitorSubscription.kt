/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.monitor

/** An open stream, which its owner closes when the browser goes away. */
fun interface MonitorSubscription : AutoCloseable {
    override fun close()
}
