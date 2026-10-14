/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.publish

import io.brulejr.iotpipeline.transform.OutboundMessage

/** Where outbound messages are delivered. */
interface PublisherPort {

    /**
     * Delivers [message], connecting first if need be. Returns once the broker has
     * accepted it, and throws if it did not: the caller decides what happens next.
     */
    fun publish(message: OutboundMessage)
}
