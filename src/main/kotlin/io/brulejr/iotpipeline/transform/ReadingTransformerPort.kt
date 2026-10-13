/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.transform

import io.brulejr.iotpipeline.parse.ParsedReading

/**
 * Reshapes a stored, consumer-neutral reading into what one consumer expects.
 *
 * The design keeps everything up to storage free of any consumer's vocabulary, so a
 * second consumer is a second implementation of this port, not a change upstream.
 */
fun interface ReadingTransformerPort {

    /** The messages [reading] becomes, in the order they must be delivered. */
    fun transform(reading: ParsedReading): List<OutboundMessage>
}
