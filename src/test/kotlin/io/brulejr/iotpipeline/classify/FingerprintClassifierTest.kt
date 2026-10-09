/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify

import io.brulejr.iotpipeline.fingerprint.FingerprintDatafill
import io.brulejr.iotpipeline.fingerprint.FingerprintService
import io.brulejr.iotpipeline.fingerprint.FingerprintedReading
import io.brulejr.iotpipeline.ingest.SensorEnvelope
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class FingerprintClassifierTest {

    private val jsonMapper = JsonMapper.builder().build()
    private val fingerprintService = FingerprintService(FingerprintDatafill(), jsonMapper)

    private fun reading(json: String): FingerprintedReading {
        val envelope = SensorEnvelope("rtl433", "rtl_433/pi/events", Instant.now(), jsonMapper.readTree(json))
        return FingerprintedReading(envelope, fingerprintService.fingerprint(envelope))
    }

    private val tower =
        """{"model":"Acurite-Tower","id":3064,"channel":"A","temperature_C":26.5,"humidity":47}"""

    @Test
    fun `an unseen structure registers itself as a new model`() {
        val registry = InMemoryModelRegistry()

        val classification = FingerprintClassifier(registry).classify(reading(tower))

        assertEquals(1, registry.all().size)
        assertEquals("Acurite-Tower", classification.model.name)
        assertEquals(DeviceKey("rtl433", "Acurite-Tower/A/3064"), classification.deviceKey)
    }

    @Test
    fun `a newly discovered model is not recognised until it is curated`() {
        val classification = assertIs<Classification.Unrecognised>(
            FingerprintClassifier(InMemoryModelRegistry()).classify(reading(tower)),
        )

        assertEquals(FingerprintClassifier.NOT_CURATED, classification.reason)
    }

    @Test
    fun `a curated model is recognised`() {
        val registry = InMemoryModelRegistry()
        val classifier = FingerprintClassifier(registry)
        val reading = reading(tower)
        // Discovery first, then a human supplies the mappings.
        val discovered = registry.registerIfAbsent(reading)
        registry.curate(discovered.fingerprint, listOf(SensorMapping("temperature_C", "temperature", "C")))

        val classification = assertIs<Classification.Recognised>(classifier.classify(reading))

        assertEquals(1, classification.model.sensors.size)
    }

    @Test
    fun `curating an unknown fingerprint changes nothing`() {
        val registry = InMemoryModelRegistry()

        assertNull(registry.curate("not-a-fingerprint", listOf(SensorMapping("x", "y"))))
        assertEquals(0, registry.all().size)
    }

    @Test
    fun `clearing the mappings makes a model unrecognised again`() {
        val registry = InMemoryModelRegistry()
        val classifier = FingerprintClassifier(registry)
        val reading = reading(tower)
        val discovered = registry.registerIfAbsent(reading)
        registry.curate(discovered.fingerprint, listOf(SensorMapping("temperature_C", "temperature")))

        registry.curate(discovered.fingerprint, emptyList())

        assertIs<Classification.Unrecognised>(classifier.classify(reading))
    }

    @Test
    fun `two readings of the same structure register one model`() {
        val registry = InMemoryModelRegistry()
        val classifier = FingerprintClassifier(registry)

        classifier.classify(reading(tower))
        // Different values, same fields: the same model.
        classifier.classify(reading("""{"model":"Acurite-Tower","id":9981,"channel":"B","temperature_C":4.0,"humidity":90}"""))

        assertEquals(1, registry.all().size)
    }

    @Test
    fun `a different field set registers a second model`() {
        val registry = InMemoryModelRegistry()
        val classifier = FingerprintClassifier(registry)

        classifier.classify(reading(tower))
        classifier.classify(reading("""{"model":"DSC-Security","id":2320475,"closed":1,"tamper":0}"""))

        assertEquals(2, registry.all().size)
    }

    @Test
    fun `a curated model whose payload names no device stays unrecognised`() {
        val registry = InMemoryModelRegistry()
        val classifier = FingerprintClassifier(registry)
        val anonymous = reading("""{"temp":1.0}""")
        val discovered = registry.registerIfAbsent(anonymous)
        registry.curate(discovered.fingerprint, listOf(SensorMapping("temp", "temperature")))

        val classification = assertIs<Classification.Unrecognised>(classifier.classify(anonymous))

        assertEquals(FingerprintClassifier.NO_DEVICE_IDENTITY, classification.reason)
        assertNull(classification.deviceKey)
    }

    @Test
    fun `find returns a registered model and nothing for an unseen fingerprint`() {
        val registry = InMemoryModelRegistry()
        val discovered = registry.registerIfAbsent(reading(tower))

        assertEquals(discovered, registry.find(discovered.fingerprint))
        assertNull(registry.find("unseen"))
    }
}
