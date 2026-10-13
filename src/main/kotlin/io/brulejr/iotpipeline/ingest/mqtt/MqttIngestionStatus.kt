/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.ingest.mqtt

import java.time.Instant

/**
 * The broker connection and what has arrived over it since startup.
 *
 * @property connected null until the adapter has either subscribed or failed to connect.
 * @property received messages wrapped and sent down the pipeline.
 * @property malformed messages dropped because their body was not JSON.
 * @property lastReceivedAt when the last message arrived, or null if none has.
 * @property lastFailure why the connection last failed or was lost, or null if it never has.
 */
data class MqttIngestionStatus(
    val connected: Boolean?,
    val received: Long,
    val malformed: Long,
    val lastReceivedAt: Instant?,
    val lastFailure: String?,
)
