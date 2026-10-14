/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.publish

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** Where messages are published and how outages are retried. Credentials are left out. */
class PublishInfoContributor(private val datafill: PublishDatafill) : InfoContributor {

    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "publish",
            mapOf(
                "enabled" to datafill.enabled,
                "mqtt" to mapOf(
                    "url" to datafill.mqtt.url,
                    "clientId" to datafill.mqtt.clientId,
                    "qos" to datafill.mqtt.qos,
                    "authenticated" to (datafill.mqtt.username != null),
                ),
                "retry" to mapOf(
                    "initialBackoff" to datafill.retry.initialBackoff.toString(),
                    "maxBackoff" to datafill.retry.maxBackoff.toString(),
                ),
            ),
        )
    }
}
