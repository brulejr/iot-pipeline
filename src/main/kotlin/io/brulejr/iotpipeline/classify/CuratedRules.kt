/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify

/**
 * The curated classification rule set, maintained by hand.
 *
 * Add a rule when a device model should become recognisable. Order matters: the first
 * matching rule wins, so a narrow rule belongs before a broader one. Recognising a
 * model does not promote any device to known; that stays a manual step.
 */
val CURATED_CLASSIFICATION_RULES: List<ClassificationRule> = classificationRules {
    // Acurite 592TXR / Tower outdoor temperature-humidity sensor.
    rtl433("Acurite-Tower", parseRuleSetId = "acurite-tower-v1", "temperature_C", "humidity")
}
