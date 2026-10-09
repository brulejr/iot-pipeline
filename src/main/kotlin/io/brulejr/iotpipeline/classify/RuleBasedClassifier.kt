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

/**
 * Identifies a reading's device model by walking an ordered, manually curated rule
 * set and taking the first match.
 *
 * Recognising the model is all this stage does; it says nothing about whether the
 * device has been promoted to known. A reading no rule recognises is still passed
 * along as [Classification.Unidentified], because the recommendation engine works on
 * raw payloads.
 */
class RuleBasedClassifier(private val rules: List<ClassificationRule>) : ClassificationPort {

    override fun classify(envelope: SensorEnvelope): Classification {
        val deviceKey = DeviceKeys.of(envelope)
        val rule = rules.firstOrNull { it.matches(envelope) }
            ?: return Classification.Unidentified(envelope, deviceKey, NO_RULE_MATCHED)

        // A rule claimed the payload but it names no device, so there is nothing
        // stable to promote or count later. Treat it as unidentified rather than
        // inventing an identity.
        if (deviceKey == null) {
            return Classification.Unidentified(envelope, null, "matched $rule but the payload identifies no device")
        }
        return Classification.Identified(envelope, deviceKey, rule.model, rule.parseRuleSetId)
    }

    companion object {
        const val NO_RULE_MATCHED = "no classification rule recognised the model"
    }
}
