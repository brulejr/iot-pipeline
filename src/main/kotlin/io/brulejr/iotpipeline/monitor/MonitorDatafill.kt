/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.monitor

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * The live monitor: every classified reading, streamed to whoever is watching, and the
 * devices heard recently.
 *
 * @property maxSubscribers how many streams may be open at once. Each holds a queue and
 *   a thread, so the cap keeps a forgotten browser tab from costing much.
 * @property queueCapacity events held for one stream while it catches up. A stream that
 *   falls further behind loses the overflow rather than slowing the pipeline.
 * @property heartbeat how often an idle stream is sent a comment, so a closed browser is
 *   noticed and proxies do not time the connection out.
 * @property streamTimeout how long one stream lasts before the browser is asked to
 *   reconnect, which it does on its own.
 * @property heardFor how long a device is remembered after it was last heard.
 * @property heardDevices how many devices are remembered at most, the least recently
 *   heard forgotten first.
 */
@ConfigurationProperties("pipeline.monitor")
data class MonitorDatafill(
    val maxSubscribers: Int = 8,
    val queueCapacity: Int = 256,
    val heartbeat: Duration = Duration.ofSeconds(15),
    val streamTimeout: Duration = Duration.ofHours(1),
    val heardFor: Duration = Duration.ofHours(1),
    val heardDevices: Long = 1000,
) {
    init {
        require(maxSubscribers > 0) { "pipeline.monitor.max-subscribers must be positive" }
        require(queueCapacity > 0) { "pipeline.monitor.queue-capacity must be positive" }
        require(heartbeat.isPositive) { "pipeline.monitor.heartbeat must be positive" }
        require(streamTimeout > heartbeat) { "pipeline.monitor.stream-timeout must be longer than heartbeat" }
        require(heardFor.isPositive) { "pipeline.monitor.heard-for must be positive" }
        require(heardDevices > 0) { "pipeline.monitor.heard-devices must be positive" }
    }
}
