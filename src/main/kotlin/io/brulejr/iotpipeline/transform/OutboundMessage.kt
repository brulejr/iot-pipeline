/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.transform

/**
 * One message ready for a consumer, carrying everything publication needs and nothing
 * it would have to work out.
 *
 * @property retained whether the broker should keep it for subscribers that connect
 *   later. Decided here rather than at publication, because it is a property of what the
 *   message means to the consumer.
 */
data class OutboundMessage(
    val topic: String,
    val payload: String,
    val retained: Boolean,
)
