/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.fingerprint

import io.brulejr.iotpipeline.ingest.SensorEnvelope
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FingerprintServiceTest {

    private val jsonMapper = JsonMapper.builder().build()

    private fun service(datafill: FingerprintDatafill = FingerprintDatafill()) =
        FingerprintService(datafill, jsonMapper)

    private fun envelope(json: String, origin: String = "rtl_433/pi/events") =
        SensorEnvelope("rtl433", origin, Instant.now(), jsonMapper.readTree(json))

    /** Two receivers hearing one transmission: same reading, different radio metadata. */
    private val viaReceiverA =
        """{"time":"1790704138.6","model":"Acurite-Tower","id":3064,"channel":"A",""" +
            """"temperature_C":26.5,"humidity":47,"rssi":-0.11,"snr":16.2,"noise":-16.3}"""
    private val viaReceiverB =
        """{"time":"1790704138.9","model":"Acurite-Tower","id":3064,"channel":"A",""" +
            """"temperature_C":26.5,"humidity":47,"rssi":-0.13,"snr":15.7,"noise":-15.8}"""

    @Test
    fun `field order does not change any hash`() {
        val service = service()

        val ordered = service.fingerprint(envelope("""{"model":"X","id":1,"temperature_C":2.0}"""))
        val shuffled = service.fingerprint(envelope("""{"temperature_C":2.0,"id":1,"model":"X"}"""))

        assertEquals(ordered.event, shuffled.event)
        assertEquals(ordered.model, shuffled.model)
        assertEquals(ordered.device, shuffled.device)
    }

    @Test
    fun `one device heard by two receivers shares a device hash`() {
        val service = service()

        assertEquals(
            service.fingerprint(envelope(viaReceiverA, origin = "rtl_433/recv-a/events")).device,
            service.fingerprint(envelope(viaReceiverB, origin = "rtl_433/recv-b/events")).device,
        )
    }

    @Test
    fun `radio metadata is excluded from the model structure, so receivers agree on the model`() {
        val service = service()

        assertEquals(
            service.fingerprint(envelope(viaReceiverA)).model,
            service.fingerprint(envelope(viaReceiverB)).model,
        )
    }

    @Test
    fun `the model structure describes types, not values`() {
        val structure = service().modelStructure(
            jsonMapper.readTree("""{"model":"Acurite-Tower","id":3064,"wet":true,"extra":null}"""),
        )

        assertEquals("""{"extra":"null","id":"number","model":"string","wet":"boolean"}""", structure)
    }

    @Test
    fun `a different set of fields is a different model`() {
        val service = service()
        val tower = envelope("""{"model":"Acurite-Tower","id":1,"temperature_C":1.0,"humidity":2}""")
        val contact = envelope("""{"model":"DSC-Security","id":2,"closed":1,"tamper":0}""")

        assertNotEquals(service.fingerprint(tower).model, service.fingerprint(contact).model)
    }

    @Test
    fun `by default two deliveries of one transmission share an event hash`() {
        val service = service()

        // Radio metadata is excluded by default, so the drifting rssi, snr and time of
        // a repeated delivery do not make it look like a new reading.
        assertEquals(
            service.fingerprint(envelope(viaReceiverA)).event,
            service.fingerprint(envelope(viaReceiverB)).event,
        )
    }

    @Test
    fun `making every field significant keeps repeated deliveries distinct`() {
        val service = service(FingerprintDatafill(excludedEventFields = emptySet()))

        assertNotEquals(
            service.fingerprint(envelope(viaReceiverA)).event,
            service.fingerprint(envelope(viaReceiverB)).event,
        )
    }

    @Test
    fun `a genuinely different reading from one device still hashes differently`() {
        val service = service()
        val warmer = viaReceiverA.replace("\"temperature_C\":26.5", "\"temperature_C\":27.0")

        assertNotEquals(
            service.fingerprint(envelope(viaReceiverA)).event,
            service.fingerprint(envelope(warmer)).event,
        )
    }

    @Test
    fun `exclusions apply at any depth`() {
        val service = service(FingerprintDatafill(excludedModelFields = setOf("noise")))

        val structure = service.modelStructure(
            jsonMapper.readTree("""{"model":"X","noise":-1.0,"meta":{"noise":-2.0,"keep":1}}"""),
        )

        assertTrue("noise" !in structure, structure)
        assertTrue("keep" in structure, structure)
    }

    @Test
    fun `disabling the feature makes every field significant`() {
        val service = service(FingerprintDatafill(enabled = false))

        // rssi now contributes to the structure, so the default exclusions are ignored.
        assertTrue("rssi" in service.modelStructure(jsonMapper.readTree("""{"rssi":-1.0}""")))
    }
}
