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
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublishServiceTest {

    /** A clock the test moves by hand. */
    private class TestClock(var now: Instant) : Clock() {
        override fun instant() = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = this
    }

    /** A broker that can be taken down, recording what it accepted. */
    private class FakeBroker : PublisherPort {
        var up = true
        val received = mutableListOf<OutboundMessage>()
        override fun publish(message: OutboundMessage) {
            if (!up) throw IllegalStateException("broker down")
            received += message
        }
    }

    private val clock = TestClock(Instant.parse("2026-10-13T12:00:00Z"))
    private val broker = FakeBroker()
    private val datafill = PublishDatafill(
        retry = PublishDatafill.Retry(initialBackoff = Duration.ofSeconds(1), maxBackoff = Duration.ofSeconds(4)),
    )
    private val service = PublishService(broker, datafill, clock)

    private fun discovery(id: String) = OutboundMessage("homeassistant/device/$id/config", "config $id", retained = true)
    private fun state(id: String, value: Int) = OutboundMessage("iot-pipeline/$id/state", "state $value", retained = true)

    @Test
    fun `a healthy broker gets each message at once`() {
        service.publish(discovery("a"))
        service.publish(state("a", 1))

        assertEquals(listOf(discovery("a"), state("a", 1)), broker.received)
        assertEquals(2, service.stats().published)
        assertFalse(service.stats().circuitOpen)
    }

    @Test
    fun `a failed message is held and the circuit opens`() {
        broker.up = false

        service.publish(discovery("a"))

        val stats = service.stats()
        assertTrue(stats.circuitOpen)
        assertEquals(listOf(discovery("a")), service.held())
        assertEquals(clock.now.plusSeconds(1), stats.nextAttemptAt)
        assertEquals("broker down", stats.lastFailure)
    }

    @Test
    fun `while the circuit is open nothing new is tried, so nothing overtakes what is held`() {
        broker.up = false
        service.publish(discovery("a"))
        broker.up = true

        service.publish(state("a", 1))

        assertTrue(broker.received.isEmpty())
        assertEquals(listOf(discovery("a"), state("a", 1)), service.held())
    }

    @Test
    fun `only the latest message per topic is held`() {
        broker.up = false
        service.publish(discovery("a"))
        service.publish(state("a", 1))
        service.publish(state("b", 1))
        service.publish(state("a", 2))

        // The replacement for a joins the back of the line.
        assertEquals(listOf(discovery("a"), state("b", 1), state("a", 2)), service.held())
        assertEquals(1, service.stats().superseded)
    }

    @Test
    fun `held messages go out in order once the backoff has passed`() {
        broker.up = false
        service.publish(discovery("a"))
        service.publish(state("a", 1))
        broker.up = true

        service.retryIfDue()
        assertTrue(broker.received.isEmpty(), "retried before the backoff passed")

        clock.now = clock.now.plusSeconds(1)
        service.retryIfDue()

        assertEquals(listOf(discovery("a"), state("a", 1)), broker.received)
        assertTrue(service.held().isEmpty())
        assertFalse(service.stats().circuitOpen)
        assertEquals(null, service.stats().nextAttemptAt)
    }

    @Test
    fun `the backoff doubles with each failed retry, up to the maximum`() {
        broker.up = false
        service.publish(discovery("a"))
        val waits = (1..4).map {
            val due = service.stats().nextAttemptAt!!
            val wait = Duration.between(clock.now, due).seconds
            clock.now = due
            service.retryIfDue()
            wait
        }

        assertEquals(listOf(1L, 2L, 4L, 4L), waits)
    }

    @Test
    fun `recovery resets the backoff`() {
        broker.up = false
        service.publish(discovery("a"))
        clock.now = service.stats().nextAttemptAt!!
        service.retryIfDue()
        broker.up = true
        assertTrue(service.flush())

        broker.up = false
        service.publish(state("a", 1))

        assertEquals(clock.now.plusSeconds(1), service.stats().nextAttemptAt)
    }

    @Test
    fun `a retry that fails partway keeps what was not delivered`() {
        val flaky = object : PublisherPort {
            var accept = 0
            val received = mutableListOf<OutboundMessage>()
            override fun publish(message: OutboundMessage) {
                if (accept-- <= 0) throw IllegalStateException("broker down")
                received += message
            }
        }
        val service = PublishService(flaky, datafill, clock)
        service.publish(discovery("a"))
        service.publish(state("a", 1))

        flaky.accept = 1
        assertFalse(service.flush())

        assertEquals(listOf(discovery("a")), flaky.received)
        assertEquals(listOf(state("a", 1)), service.held())
    }

    @Test
    fun `nothing is sent when publication is off`() {
        val service = PublishService(broker, PublishDatafill(enabled = false), clock)

        service.publish(state("a", 1))

        assertTrue(broker.received.isEmpty())
        assertEquals(1, service.stats().dropped)
    }

    @Test
    fun `an open circuit is reported down`() {
        broker.up = false
        service.publish(discovery("a"))

        assertEquals("DOWN", PublishHealthIndicator(service, datafill).health()?.status?.code)
    }
}
