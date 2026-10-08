/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.ingest

import tools.jackson.databind.JsonNode
import java.time.Instant

/**
 * Adapter-agnostic wrapper around one inbound reading. Ingestion adapters produce
 * these so that classification and later stages never deal with transport details.
 *
 * @property source coarse source-level annotation identifying where the data came from
 *   (e.g. `rtl433`); one input to classification rules.
 * @property origin transport-specific locator, such as the MQTT topic.
 * @property receivedAt when the pipeline received the message.
 * @property payload the decoded JSON body as sent by the source.
 */
data class SensorEnvelope(
    val source: String,
    val origin: String,
    val receivedAt: Instant,
    val payload: JsonNode,
)
