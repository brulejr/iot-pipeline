/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.monitor

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.brulejr.iotpipeline.classify.Classification
import io.brulejr.iotpipeline.classify.DeviceKey
import io.brulejr.iotpipeline.promote.PromotionService
import org.slf4j.LoggerFactory
import tools.jackson.databind.JsonNode
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Watches every classified reading as it passes, for identifying a device by exercising
 * it: open a door, and the reading that arrives with `closed` changed is that door's.
 *
 * Remembers the devices heard recently and what each last sent, so a reading can be
 * compared with the one before it from the same device, and streams readings to whoever
 * is watching. The pipeline only ever hands a reading over: each stream has its own
 * bounded queue and thread, so a slow or vanished browser loses events instead of
 * holding up the pipeline, and nothing here can make a reading fail.
 *
 * @param radioFields receiver metadata, which changes on every transmission and so is
 *   never reported as a change.
 * @param signalField the payload field read as signal strength.
 */
class MonitorService(
    private val datafill: MonitorDatafill,
    private val promotions: PromotionService,
    private val radioFields: Set<String>,
    private val signalField: String,
) : AutoCloseable {

    private val log = LoggerFactory.getLogger("pipeline.monitor")

    private val heard: Cache<DeviceKey, HeardDevice> = Caffeine.newBuilder()
        .expireAfterWrite(datafill.heardFor)
        .maximumSize(datafill.heardDevices)
        .build()

    private val streams = ConcurrentHashMap<Long, Stream>()
    private val streamIds = AtomicLong()
    private val sequence = AtomicLong()
    private val observed = AtomicLong()
    private val delivered = AtomicLong()
    private val dropped = AtomicLong()

    /** Notes a reading and passes it to every stream that wants it. Never throws. */
    fun observe(classification: Classification) {
        try {
            observed.incrementAndGet()
            val envelope = classification.reading.envelope
            val deviceKey = classification.deviceKey
            val signal = envelope.payload.get(signalField)?.takeIf { it.isNumber }?.asDouble()
            var previous: HeardDevice? = null
            if (deviceKey != null) {
                heard.asMap().compute(deviceKey) { _, last ->
                    previous = last
                    HeardDevice(
                        deviceKey = deviceKey,
                        modelName = classification.model.name,
                        modelFingerprint = classification.model.fingerprint,
                        firstHeard = last?.firstHeard ?: envelope.receivedAt,
                        lastHeard = envelope.receivedAt,
                        readings = (last?.readings ?: 0) + 1,
                        signal = signal,
                        payload = envelope.payload,
                    )
                }
            }
            if (streams.isEmpty()) return
            val event = MonitorEvent(
                sequence = sequence.incrementAndGet(),
                receivedAt = envelope.receivedAt,
                origin = envelope.origin,
                deviceKey = deviceKey,
                modelName = classification.model.name,
                modelFingerprint = classification.model.fingerprint,
                modelCurated = classification.model.recognised,
                promotion = deviceKey?.let { promotions.find(it) },
                signal = signal,
                fields = fields(envelope.payload, previous?.payload),
                firstHeard = deviceKey != null && previous == null,
            )
            streams.values.forEach { it.offer(event) }
        } catch (e: Exception) {
            log.warn("Could not show a reading on the live monitor", e)
        }
    }

    /**
     * Opens a stream of the readings [filter] matches, delivered to [sink] until either
     * side closes it. Null when the most streams allowed are already open.
     */
    fun subscribe(filter: MonitorFilter, sink: MonitorSink): MonitorSubscription? {
        if (streams.size >= datafill.maxSubscribers) return null
        val id = streamIds.incrementAndGet()
        val stream = Stream(id, filter, sink)
        streams[id] = stream
        Thread.ofVirtual().name("monitor-stream-$id").start(stream::deliver)
        return MonitorSubscription(stream::close)
    }

    /** Every device heard recently, most recently heard first. */
    fun heard(): List<HeardDevice> = heard.asMap().values.sortedByDescending { it.lastHeard }

    /** The device, if it has been heard recently. */
    fun heard(deviceKey: DeviceKey): HeardDevice? = heard.getIfPresent(deviceKey)

    /** The devices of one model heard recently, most recently heard first. */
    fun heardOfModel(modelFingerprint: String): List<HeardDevice> =
        heard().filter { it.modelFingerprint == modelFingerprint }

    fun stats() = MonitorStats(streams.size, observed.get(), delivered.get(), dropped.get(), heard.estimatedSize())

    /** Ends every stream, so none outlives the application. */
    override fun close() {
        streams.values.forEach { it.close() }
    }

    /**
     * The payload's top-level fields, each compared with what the device sent last time.
     * Nothing is marked changed without a previous payload to compare with.
     */
    private fun fields(payload: JsonNode, previous: JsonNode?): List<MonitorField> =
        payload.properties().map { (name, value) ->
            val radio = name in radioFields
            val before = previous?.get(name)
            val changed = previous != null && !radio && before != value
            MonitorField(
                name = name,
                value = text(value),
                changed = changed,
                previous = if (changed) before?.let(::text) else null,
                radio = radio,
            )
        }

    private fun text(node: JsonNode): String = if (node.isValueNode) node.asString() else node.toString()

    /** One open stream: its filter, its queue and the thread draining it into the sink. */
    private inner class Stream(val id: Long, val filter: MonitorFilter, val sink: MonitorSink) {

        private val queue = ArrayBlockingQueue<MonitorEvent>(datafill.queueCapacity)
        private val open = AtomicBoolean(true)
        @Volatile private var thread: Thread? = null

        fun offer(event: MonitorEvent) {
            if (!filter.matches(event)) return
            if (!queue.offer(event)) dropped.incrementAndGet()
        }

        fun deliver() {
            thread = Thread.currentThread()
            try {
                while (open.get()) {
                    val event = queue.poll(datafill.heartbeat.toMillis(), TimeUnit.MILLISECONDS)
                    if (!open.get()) break
                    if (event == null) {
                        sink.heartbeat()
                    } else {
                        sink.event(event)
                        delivered.incrementAndGet()
                    }
                }
            } catch (_: InterruptedException) {
                // Closed while waiting.
            } catch (e: Exception) {
                // The browser went away; nothing to report.
                log.debug("Monitor stream {} ended: {}", id, e.message)
            } finally {
                close()
            }
        }

        fun close() {
            if (!open.compareAndSet(true, false)) return
            streams.remove(id)
            thread?.takeIf { it != Thread.currentThread() }?.interrupt()
            try {
                sink.closed()
            } catch (e: Exception) {
                log.debug("Monitor stream {} did not close cleanly: {}", id, e.message)
            }
        }
    }
}
