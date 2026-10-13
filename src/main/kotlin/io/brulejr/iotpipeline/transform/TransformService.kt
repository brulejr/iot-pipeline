/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.transform

import io.brulejr.iotpipeline.parse.ParsedReading
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Stage 6: reshapes each stored reading into the messages a consumer expects, and keeps
 * the latest message for each topic so what would be published can be inspected.
 *
 * One message is kept per topic, and topics are per device, so what is held grows with
 * the number of promoted devices, not with traffic.
 */
class TransformService(
    private val transformer: ReadingTransformerPort,
    private val datafill: TransformDatafill,
) {

    private val readings = AtomicLong()
    private val messages = AtomicLong()
    private val latestByTopic = ConcurrentHashMap<String, OutboundMessage>()

    /** The messages [reading] becomes, in delivery order; none when transformation is off. */
    fun transform(reading: ParsedReading): List<OutboundMessage> {
        if (!datafill.enabled) return emptyList()
        val outbound = transformer.transform(reading)
        readings.incrementAndGet()
        messages.addAndGet(outbound.size.toLong())
        outbound.forEach { latestByTopic[it.topic] = it }
        return outbound
    }

    /** The last message produced for each topic, ordered by topic. */
    fun latest(): List<OutboundMessage> = latestByTopic.values.sortedBy { it.topic }

    fun stats() = TransformStats(readings.get(), messages.get(), latestByTopic.size)
}
