/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.monitor

/**
 * Where one stream's events go, typically a browser connection. Called from the
 * stream's own thread, never the pipeline's, one call at a time.
 *
 * An exception from [event] or [heartbeat] ends the stream: the browser has gone.
 */
interface MonitorSink {

    fun event(event: MonitorEvent)

    /** Nothing has happened for a while; proves the connection is still there. */
    fun heartbeat()

    /** The stream has ended, from either side. Called once. */
    fun closed() {}
}
