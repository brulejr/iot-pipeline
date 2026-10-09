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
        val registry = InMemoryModelRegistry(jsonMapper)

        val classification = FingerprintClassifier(registry).classify(reading(tower))

        assertEquals(1, registry.all().size)
        assertEquals("Acurite-Tower", classification.model.name)
        assertEquals(DeviceKey("rtl433", "Acurite-Tower/A/3064"), classification.deviceKey)
    }

    @Test
    fun `a newly discovered model is not recognised until it is curated`() {
        val classification = assertIs<Classification.Unrecognised>(
            FingerprintClassifier(InMemoryModelRegistry(jsonMapper)).classify(reading(tower)),
        )

        assertEquals(FingerprintClassifier.NOT_CURATED, classification.reason)
    }

    @Test
    fun `a curated model is recognised`() {
        val registry = InMemoryModelRegistry(jsonMapper)
        val classifier = FingerprintClassifier(registry)
        val reading = reading(tower)
        // Discovery first, then a human supplies the mappings.
        val discovered = registry.registerIfAbsent(reading)
        assertIs<CurationResult.Curated>(
            registry.curate(discovered.fingerprint, listOf(SensorMapping("temperature_C", SensorType.ANALOG, "temperature"))),
        )

        val classification = assertIs<Classification.Recognised>(classifier.classify(reading))

        assertEquals(1, classification.model.sensors.size)
    }

    @Test
    fun `curating an unknown fingerprint reports the model missing`() {
        val registry = InMemoryModelRegistry(jsonMapper)

        val result = registry.curate("not-a-fingerprint", listOf(SensorMapping("x", SensorType.ANALOG, "y")))

        assertIs<CurationResult.ModelNotFound>(result)
        assertEquals(0, registry.all().size)
    }

    @Test
    fun `a mapping naming a field the model does not have is rejected`() {
        val registry = InMemoryModelRegistry(jsonMapper)
        val discovered = registry.registerIfAbsent(reading(tower))

        val result = registry.curate(
            discovered.fingerprint,
            listOf(
                SensorMapping("temperature_C", SensorType.ANALOG, "temperature"),
                SensorMapping("not_a_real_field", SensorType.ANALOG, "temperature"),
            ),
        )

        val rejected = assertIs<CurationResult.UnknownFields>(result)
        assertEquals(setOf("not_a_real_field"), rejected.fields)
        // Rejected whole: the valid mapping in the same request is not stored either.
        assertEquals(false, registry.find(discovered.fingerprint)!!.recognised)
    }

    @Test
    fun `a rejected curation leaves existing mappings untouched`() {
        val registry = InMemoryModelRegistry(jsonMapper)
        val discovered = registry.registerIfAbsent(reading(tower))
        registry.curate(discovered.fingerprint, listOf(SensorMapping("humidity", SensorType.ANALOG, "humidity")))

        registry.curate(discovered.fingerprint, listOf(SensorMapping("bogus", SensorType.ANALOG, "temperature")))

        val stored = registry.find(discovered.fingerprint)!!
        assertEquals(listOf("humidity"), stored.sensors.map { it.name })
    }

    @Test
    fun `a field nested inside the structure can be mapped`() {
        val registry = InMemoryModelRegistry(jsonMapper)
        val nested = registry.registerIfAbsent(
            reading("""{"model":"Probe","id":5,"channel":"A","inner":{"depth_cm":12.0}}"""),
        )

        val result = registry.curate(
            nested.fingerprint,
            listOf(SensorMapping("depth_cm", SensorType.ANALOG, "distance")),
        )

        assertIs<CurationResult.Curated>(result)
    }

    @Test
    fun `clearing the mappings makes a model unrecognised again`() {
        val registry = InMemoryModelRegistry(jsonMapper)
        val classifier = FingerprintClassifier(registry)
        val reading = reading(tower)
        val discovered = registry.registerIfAbsent(reading)
        registry.curate(discovered.fingerprint, listOf(SensorMapping("temperature_C", SensorType.ANALOG, "temperature")))

        registry.curate(discovered.fingerprint, emptyList())

        assertIs<Classification.Unrecognised>(classifier.classify(reading))
    }

    @Test
    fun `two readings of the same structure register one model`() {
        val registry = InMemoryModelRegistry(jsonMapper)
        val classifier = FingerprintClassifier(registry)

        classifier.classify(reading(tower))
        // Different values, same fields: the same model.
        classifier.classify(reading("""{"model":"Acurite-Tower","id":9981,"channel":"B","temperature_C":4.0,"humidity":90}"""))

        assertEquals(1, registry.all().size)
    }

    @Test
    fun `a different field set registers a second model`() {
        val registry = InMemoryModelRegistry(jsonMapper)
        val classifier = FingerprintClassifier(registry)

        classifier.classify(reading(tower))
        classifier.classify(reading("""{"model":"DSC-Security","id":2320475,"closed":1,"tamper":0}"""))

        assertEquals(2, registry.all().size)
    }

    @Test
    fun `a curated model whose payload names no device stays unrecognised`() {
        val registry = InMemoryModelRegistry(jsonMapper)
        val classifier = FingerprintClassifier(registry)
        val anonymous = reading("""{"temp":1.0}""")
        val discovered = registry.registerIfAbsent(anonymous)
        registry.curate(discovered.fingerprint, listOf(SensorMapping("temp", SensorType.ANALOG, "temperature")))

        val classification = assertIs<Classification.Unrecognised>(classifier.classify(anonymous))

        assertEquals(FingerprintClassifier.NO_DEVICE_IDENTITY, classification.reason)
        assertNull(classification.deviceKey)
    }

    @Test
    fun `find returns a registered model and nothing for an unseen fingerprint`() {
        val registry = InMemoryModelRegistry(jsonMapper)
        val discovered = registry.registerIfAbsent(reading(tower))

        assertEquals(discovered, registry.find(discovered.fingerprint))
        assertNull(registry.find("unseen"))
    }
}
