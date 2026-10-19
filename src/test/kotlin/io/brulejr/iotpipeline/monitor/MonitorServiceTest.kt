/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.monitor

import io.brulejr.iotpipeline.classify.Classification
import io.brulejr.iotpipeline.classify.ClassificationService
import io.brulejr.iotpipeline.classify.DeviceKey
import io.brulejr.iotpipeline.classify.InMemoryModelRegistry
import io.brulejr.iotpipeline.fingerprint.FingerprintDatafill
import io.brulejr.iotpipeline.fingerprint.FingerprintService
import io.brulejr.iotpipeline.fingerprint.FingerprintedReading
import io.brulejr.iotpipeline.ingest.SensorEnvelope
import io.brulejr.iotpipeline.promote.InMemoryPromotionRegistry
import io.brulejr.iotpipeline.promote.PromotedDevice
import io.brulejr.iotpipeline.promote.PromotionPort
import io.brulejr.iotpipeline.promote.PromotionService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MonitorServiceTest {

    private val jsonMapper = JsonMapper.builder().build()
    private val fingerprints = FingerprintService(FingerprintDatafill(), jsonMapper)
    private val classification = ClassificationService(InMemoryModelRegistry(), jsonMapper)
    private val promotionRegistry = InMemoryPromotionRegistry()
    private val promotions = PromotionService(promotionRegistry)
    private val datafill = MonitorDatafill(maxSubscribers = 2, queueCapacity = 4, heartbeat = Duration.ofMillis(200))
    private val monitor = MonitorService(datafill, promotions, FingerprintDatafill.DEFAULT_RADIO_METADATA_FIELDS, "snr")

    private val door = DeviceKey("rtl433", "DSC-Security/2328667")

    @AfterEach
    fun close() = monitor.close()

    private fun classified(json: String, at: Instant = Instant.now()): Classification {
        val envelope = SensorEnvelope("rtl433", "rtl_433/pi/events", at, jsonMapper.readTree(json))
        return classification.classify(FingerprintedReading(envelope, fingerprints.fingerprint(envelope)))
    }

    private fun contact(closed: Int, snr: Double = 15.3) =
        classified("""{"model":"DSC-Security","id":2328667,"closed":$closed,"event":1,"snr":$snr}""")

    private fun tower(temperature: Double) =
        classified("""{"model":"Acurite-Tower","id":3064,"channel":"A","temperature_C":$temperature,"snr":7.1}""")

    /** Collects what a stream delivers, for the test thread to wait on. */
    private class Recorder : MonitorSink {
        val events = LinkedBlockingQueue<MonitorEvent>()
        val heartbeats = CountDownLatch(1)
        val closed = CountDownLatch(1)

        override fun event(event: MonitorEvent) {
            events.put(event)
        }

        override fun heartbeat() = heartbeats.countDown()

        override fun closed() = closed.countDown()

        fun next(): MonitorEvent = assertNotNull(events.poll(2, TimeUnit.SECONDS), "no event arrived")

        fun nothingMore() = assertNull(events.poll(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `a device is remembered with what it last sent, whether or not anyone watches`() {
        monitor.observe(contact(closed = 1))
        monitor.observe(contact(closed = 0, snr = 16.0))

        val heard = assertNotNull(monitor.heard(door))
        assertEquals(2, heard.readings)
        assertEquals(16.0, heard.signal)
        assertEquals(0, heard.payload.get("closed").asInt())
        assertEquals(listOf(door), monitor.heardOfModel(heard.modelFingerprint).map { it.deviceKey })
    }

    @Test
    fun `a field that changed since the device's last transmission is marked, radio metadata never`() {
        monitor.observe(contact(closed = 1, snr = 15.0))
        val recorder = Recorder()
        monitor.subscribe(MonitorFilter(deviceKey = door), recorder)

        monitor.observe(contact(closed = 0, snr = 9.0))

        val event = recorder.next()
        val byName = event.fields.associateBy { it.name }
        assertTrue(event.changed)
        assertFalse(event.firstHeard)
        assertTrue(byName.getValue("closed").changed)
        assertEquals("1", byName.getValue("closed").previous)
        assertEquals("0", byName.getValue("closed").value)
        assertFalse(byName.getValue("event").changed)
        assertTrue(byName.getValue("snr").radio)
        assertFalse(byName.getValue("snr").changed, "snr differs, but is radio metadata")
        assertEquals(9.0, event.signal)
    }

    @Test
    fun `a device not heard before is flagged as first heard, with nothing marked changed`() {
        val recorder = Recorder()
        monitor.subscribe(MonitorFilter(), recorder)

        monitor.observe(contact(closed = 1))

        val event = recorder.next()
        assertTrue(event.firstHeard)
        assertTrue(event.fields.none { it.changed })
    }

    @Test
    fun `a stream sees only what its filter matches`() {
        promotionRegistry.save(PromotedDevice(door, "Front door", "door", "hall", Instant.now()))
        val unpromoted = Recorder()
        val search = Recorder()
        monitor.subscribe(MonitorFilter(scope = MonitorScope.UNPROMOTED), unpromoted)
        monitor.subscribe(MonitorFilter(text = "front"), search)

        monitor.observe(contact(closed = 1))
        monitor.observe(tower(21.0))

        assertEquals("Acurite-Tower/A/3064", unpromoted.next().deviceKey?.id)
        unpromoted.nothingMore()
        val found = search.next()
        assertEquals("Front door", found.promotion?.name, "matched by its promoted name")
        search.nothingMore()
    }

    @Test
    fun `changes only leaves out a device repeating itself`() {
        val recorder = Recorder()
        monitor.subscribe(MonitorFilter(changesOnly = true), recorder)

        monitor.observe(tower(21.0))
        monitor.observe(tower(21.0))
        monitor.observe(tower(21.5))

        assertTrue(recorder.next().firstHeard)
        assertEquals("21.5", recorder.next().fields.single { it.name == "temperature_C" }.value)
        recorder.nothingMore()
    }

    @Test
    fun `no more streams open than the limit allows`() {
        assertNotNull(monitor.subscribe(MonitorFilter(), Recorder()))
        assertNotNull(monitor.subscribe(MonitorFilter(), Recorder()))

        assertNull(monitor.subscribe(MonitorFilter(), Recorder()))
        assertEquals(2, monitor.stats().streams)
    }

    @Test
    fun `closing a stream frees its place and tells the sink`() {
        val recorder = Recorder()
        val subscription = assertNotNull(monitor.subscribe(MonitorFilter(), recorder))

        subscription.close()

        assertTrue(recorder.closed.await(2, TimeUnit.SECONDS))
        assertEquals(0, monitor.stats().streams)
    }

    @Test
    fun `a stream whose browser has gone ends itself without troubling the pipeline`() {
        val closed = CountDownLatch(1)
        monitor.subscribe(
            MonitorFilter(),
            object : MonitorSink {
                override fun event(event: MonitorEvent) = throw java.io.IOException("Broken pipe")
                override fun heartbeat() = Unit
                override fun closed() = closed.countDown()
            },
        )

        monitor.observe(contact(closed = 1))

        assertTrue(closed.await(2, TimeUnit.SECONDS))
        assertEquals(0, monitor.stats().streams)
    }

    @Test
    fun `an idle stream is sent heartbeats`() {
        val recorder = Recorder()
        monitor.subscribe(MonitorFilter(), recorder)

        assertTrue(recorder.heartbeats.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun `a stream that falls behind loses the overflow, not the pipeline's time`() {
        val release = CountDownLatch(1)
        monitor.subscribe(
            MonitorFilter(),
            object : MonitorSink {
                override fun event(event: MonitorEvent) {
                    release.await()
                }
                override fun heartbeat() = Unit
            },
        )

        repeat(20) { monitor.observe(tower(20.0 + it)) }

        assertTrue(monitor.stats().dropped > 0)
        release.countDown()
    }

    @Test
    fun `a reading the monitor cannot handle is logged, never thrown into the pipeline`() {
        val failing = MonitorService(
            datafill,
            PromotionService(object : PromotionPort by promotionRegistry {
                override fun find(deviceKey: DeviceKey) = throw IllegalStateException("PostgreSQL unreachable")
            }),
            emptySet(),
            "snr",
        )
        failing.subscribe(MonitorFilter(), Recorder())

        failing.observe(contact(closed = 1))

        assertEquals(1, failing.stats().observed)
        failing.close()
    }
}
