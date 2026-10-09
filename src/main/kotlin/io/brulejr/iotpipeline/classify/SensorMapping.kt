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
 * Maps one field in a model's payload to a reading the rest of the pipeline understands.
 *
 * @property name the payload field this mapping reads, e.g. `temperature_C`.
 * @property type whether the field carries a measurement or an on/off state.
 * @property classname the semantic class of the reading, e.g. `temperature`, `humidity`,
 *   `battery`. Downstream publishing maps it to a consumer's own vocabulary.
 * @property friendlyName optional display name for a consumer that wants one.
 * @property inverted true when the field's truth is the opposite of what [classname]
 *   means to a consumer. rtl_433 reports `battery_ok: 1` for a healthy battery and
 *   `closed: 1` for a shut contact, while Home Assistant's `battery` and `opening`
 *   classes both treat ON as the problem state. Only the person curating the model knows
 *   this, so it is recorded here rather than guessed at publish time. Meaningless for an
 *   [SensorType.ANALOG] mapping, and rejected there.
 */
data class SensorMapping(
    val name: String,
    val type: SensorType,
    val classname: String,
    val friendlyName: String? = null,
    val inverted: Boolean = false,
)
