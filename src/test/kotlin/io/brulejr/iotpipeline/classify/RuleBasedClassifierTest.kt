/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify

import io.brulejr.iotpipeline.ingest.SensorEnvelope
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RuleBasedClassifierTest {

    private val jsonMapper = JsonMapper.builder().build()

    private fun envelope(json: String, source: String = RTL433_SOURCE) =
        SensorEnvelope(source, "rtl_433/pi/events", Instant.now(), jsonMapper.readTree(json))

    private val tower =
        """{"model":"Acurite-Tower","id":3064,"channel":"A","temperature_C":26.5,"humidity":47}"""

    @Test
    fun `recognises a curated rtl_433 model`() {
        val classifier = RuleBasedClassifier(CURATED_CLASSIFICATION_RULES)

        val classification = assertIs<Classification.Identified>(classifier.classify(envelope(tower)))

        assertEquals("Acurite-Tower", classification.model)
        assertEquals("acurite-tower-v1", classification.parseRuleSetId)
        assertEquals(DeviceKey(RTL433_SOURCE, "Acurite-Tower/A/3064"), classification.deviceKey)
    }

    @Test
    fun `leaves an uncurated model unidentified but still keyed`() {
        val classifier = RuleBasedClassifier(CURATED_CLASSIFICATION_RULES)
        val neighbour = """{"model":"LaCrosse-TX141THBv2","id":99,"channel":"B","temperature_C":4.0}"""

        val classification = assertIs<Classification.Unidentified>(classifier.classify(envelope(neighbour)))

        assertEquals(RuleBasedClassifier.NO_RULE_MATCHED, classification.reason)
        // The recommendation engine needs a stable key to count sightings.
        assertEquals(DeviceKey(RTL433_SOURCE, "LaCrosse-TX141THBv2/B/99"), classification.deviceKey)
    }

    @Test
    fun `first matching rule wins`() {
        val classifier = RuleBasedClassifier(
            classificationRules {
                rtl433("Acurite-Tower", parseRuleSetId = "narrow-v1", "humidity")
                rtl433("Acurite-Tower", parseRuleSetId = "broad-v1")
            },
        )

        val classification = assertIs<Classification.Identified>(classifier.classify(envelope(tower)))

        assertEquals("narrow-v1", classification.parseRuleSetId)
    }

    @Test
    fun `a rule does not match a reading from another source`() {
        val classifier = RuleBasedClassifier(CURATED_CLASSIFICATION_RULES)

        val classification = classifier.classify(envelope(tower, source = "weather-station-rest"))

        assertIs<Classification.Unidentified>(classification)
    }

    @Test
    fun `required fields must be present for the rule to match`() {
        val classifier = RuleBasedClassifier(CURATED_CLASSIFICATION_RULES)
        // A truncated decode: the right model, but no humidity reading.
        val truncated = """{"model":"Acurite-Tower","id":3064,"channel":"A","temperature_C":26.5}"""

        assertIs<Classification.Unidentified>(classifier.classify(envelope(truncated)))
    }

    @Test
    fun `a matched payload naming no device is unidentified rather than given an identity`() {
        val classifier = RuleBasedClassifier(
            classificationRules {
                // Matches anything, so the rule fires even without a model field.
                rule("Anything", parseRuleSetId = "anything-v1")
            },
        )

        val classification = assertIs<Classification.Unidentified>(classifier.classify(envelope("""{"temp":1}""")))

        assertEquals(null, classification.deviceKey)
        assertTrue(classification.reason.contains("identifies no device"), classification.reason)
    }

    @Test
    fun `an empty rule set identifies nothing`() {
        val classification = RuleBasedClassifier(emptyList()).classify(envelope(tower))

        assertIs<Classification.Unidentified>(classification)
    }
}
