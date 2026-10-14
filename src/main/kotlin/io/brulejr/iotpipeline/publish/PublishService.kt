/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.publish

import io.brulejr.iotpipeline.transform.OutboundMessage
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Stage 7: delivers outbound messages, riding out broker outages without re-running the
 * transformation.
 *
 * A message that cannot be delivered is held, and so is everything after it until the
 * broker is back, at most one per topic: a newer message for a topic replaces the one
 * held, and moves to the back of the line. That keeps what is held bounded by the
 * number of devices however long the outage, and loses nothing that matters, since a
 * consumer only wants the latest state and every reading is in InfluxDB. It also keeps
 * discovery: the transformer sends a device's discovery once, so dropping it would
 * leave the device unknown to Home Assistant until its curation next changed.
 *
 * While anything is held the circuit is open: nothing new is tried, and the held
 * messages are retried in the order they arrived, after a backoff that doubles with
 * each failed retry. The first success closes it. What is held lives in memory: a
 * restart loses it, but the transformer sends discovery again after a restart, and the
 * next reading brings fresh state.
 */
class PublishService(
    private val publisher: PublisherPort,
    private val datafill: PublishDatafill,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger("pipeline.publish")

    // Guarded by this. Insertion-ordered, so held messages go out in arrival order.
    private val held = LinkedHashMap<String, OutboundMessage>()
    private var circuitOpen = false
    private var backoff: Duration = datafill.retry.initialBackoff
    private var nextAttemptAt: Instant? = null
    private var published = 0L
    private var superseded = 0L
    private var failures = 0L
    private var dropped = 0L
    private var lastPublishedAt: Instant? = null
    private var lastFailure: String? = null

    /** Delivers [message] now if the broker is healthy, otherwise holds it. */
    @Synchronized
    fun publish(message: OutboundMessage) {
        if (!datafill.enabled) {
            dropped++
            return
        }
        if (circuitOpen) {
            hold(message)
            return
        }
        try {
            send(message)
        } catch (e: Exception) {
            hold(message)
            trip(e)
        }
    }

    /** Retries what is held once its backoff has passed. Called on a timer. */
    @Synchronized
    fun retryIfDue() {
        val due = nextAttemptAt ?: return
        if (circuitOpen && !clock.instant().isBefore(due)) flush()
    }

    /**
     * Tries every held message now, in arrival order, stopping at the first failure.
     * Returns true when nothing is left held.
     */
    @Synchronized
    fun flush(): Boolean {
        val iterator = held.values.iterator()
        while (iterator.hasNext()) {
            val message = iterator.next()
            try {
                send(message)
            } catch (e: Exception) {
                trip(e)
                return false
            }
            iterator.remove()
        }
        if (circuitOpen) {
            log.info("Publishing recovered; every held message is delivered")
            circuitOpen = false
            backoff = datafill.retry.initialBackoff
            nextAttemptAt = null
        }
        return true
    }

    /** The messages waiting for the broker, in the order they will be tried. */
    @Synchronized
    fun held(): List<OutboundMessage> = held.values.toList()

    @Synchronized
    fun stats() = PublishStats(
        published = published,
        pending = held.size,
        superseded = superseded,
        failures = failures,
        dropped = dropped,
        circuitOpen = circuitOpen,
        lastPublishedAt = lastPublishedAt,
        lastFailure = lastFailure,
        nextAttemptAt = nextAttemptAt,
    )

    private fun send(message: OutboundMessage) {
        publisher.publish(message)
        published++
        lastPublishedAt = clock.instant()
        log.debug("published {}{}", message.topic, if (message.retained) " (retained)" else "")
    }

    private fun hold(message: OutboundMessage) {
        // Removed first so a replacement joins the back of the line.
        if (held.remove(message.topic) != null) superseded++
        held[message.topic] = message
    }

    private fun trip(e: Exception) {
        failures++
        lastFailure = e.message ?: e.javaClass.simpleName
        if (circuitOpen) {
            backoff = minOf(backoff.multipliedBy(2), datafill.retry.maxBackoff)
        } else {
            circuitOpen = true
            backoff = datafill.retry.initialBackoff
            log.warn(
                "Publishing to {} failed ({}); holding messages, latest per topic, until it recovers",
                datafill.mqtt.url, lastFailure,
            )
        }
        nextAttemptAt = clock.instant().plus(backoff)
    }
}
