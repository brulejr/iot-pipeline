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
 * What the monitor has done since startup.
 *
 * @property streams streams open now.
 * @property observed readings seen, whether or not anyone was watching.
 * @property delivered events handed to streams.
 * @property dropped events a stream had no room for because it had fallen behind.
 * @property heardDevices devices remembered as heard recently.
 */
data class MonitorStats(
    val streams: Int,
    val observed: Long,
    val delivered: Long,
    val dropped: Long,
    val heardDevices: Long,
)
