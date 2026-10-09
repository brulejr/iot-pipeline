/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.dedupe

import io.brulejr.iotpipeline.fingerprint.Fingerprint
import io.brulejr.iotpipeline.fingerprint.FingerprintedReading
import io.brulejr.iotpipeline.ingest.SensorEnvelope
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DedupeServiceTest {

    private val jsonMapper = JsonMapper.builder().build()

    private fun reading(device: String, event: String) = FingerprintedReading(
        envelope = SensorEnvelope("rtl433", "rtl_433/pi/events", Instant.now(), jsonMapper.readTree("{}")),
        fingerprint = Fingerprint(event = event, device = device, model = "model-hash", modelStructure = "{}"),
    )

    @Test
    fun `the first reading from a device is unique`() {
        assertTrue(DedupeService(DedupeDatafill()).isUnique(reading("dev-1", "evt-1")))
    }

    @Test
    fun `the same reading repeated from one device is a duplicate`() {
        val service = DedupeService(DedupeDatafill())

        assertTrue(service.isUnique(reading("dev-1", "evt-1")))
        assertFalse(service.isUnique(reading("dev-1", "evt-1")))
    }

    @Test
    fun `a new reading from the same device is unique`() {
        val service = DedupeService(DedupeDatafill())
        service.isUnique(reading("dev-1", "evt-1"))

        assertTrue(service.isUnique(reading("dev-1", "evt-2")))
    }

    @Test
    fun `devices are tracked independently`() {
        val service = DedupeService(DedupeDatafill())
        service.isUnique(reading("dev-1", "evt-1"))

        // Same event hash, different transmitter: not a duplicate of dev-1's reading.
        assertTrue(service.isUnique(reading("dev-2", "evt-1")))
    }

    @Test
    fun `a repeat outside the window is unique again`() {
        val service = DedupeService(DedupeDatafill(window = Duration.ZERO))

        assertTrue(service.isUnique(reading("dev-1", "evt-1")))
        assertTrue(service.isUnique(reading("dev-1", "evt-1")))
    }

    @Test
    fun `disabling the feature passes every reading`() {
        val service = DedupeService(DedupeDatafill(enabled = false))

        assertTrue(service.isUnique(reading("dev-1", "evt-1")))
        assertTrue(service.isUnique(reading("dev-1", "evt-1")))
    }
}
