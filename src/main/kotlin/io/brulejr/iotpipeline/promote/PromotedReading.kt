/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote

import io.brulejr.iotpipeline.classify.Classification

/**
 * A reading from a promoted device, as it travels past the gate.
 *
 * Carries the promotion as well as the classification because the stages after the gate
 * need both: the model says how to read the payload, the promotion says what the device
 * is called and where it is.
 */
data class PromotedReading(
    val classification: Classification,
    val device: PromotedDevice,
)
