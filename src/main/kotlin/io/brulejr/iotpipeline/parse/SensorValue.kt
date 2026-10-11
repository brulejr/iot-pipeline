/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.parse

import io.brulejr.iotpipeline.classify.SensorMapping

/**
 * One value read out of a payload through its [SensorMapping].
 *
 * This is the neutral form the design calls for: a number or a state, labelled by what
 * it means, with nothing left of how the source happened to encode it.
 */
sealed interface SensorValue {

    val mapping: SensorMapping

    data class Analog(override val mapping: SensorMapping, val value: Double) : SensorValue

    /**
     * @property on the state as [SensorMapping.classname] means it to a consumer, with
     *   [SensorMapping.inverted] already applied. rtl_433 sends `battery_ok: 1` for a
     *   healthy battery; under a `battery` class, where on means trouble, that arrives
     *   here as false.
     */
    data class Binary(override val mapping: SensorMapping, val on: Boolean) : SensorValue
}
