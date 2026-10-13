/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.ingest.mqtt

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** Which broker and topics readings come from. Credentials are deliberately left out. */
class MqttIngestionInfoContributor(private val datafill: MqttIngestionDatafill) : InfoContributor {

    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "ingest",
            mapOf(
                "mqtt" to mapOf(
                    "url" to datafill.url,
                    "clientId" to datafill.clientId,
                    "topics" to datafill.topics,
                    "qos" to datafill.qos,
                    "source" to datafill.source,
                    "authenticated" to (datafill.username != null),
                ),
            ),
        )
    }
}
