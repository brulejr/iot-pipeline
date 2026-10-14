/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.publish

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * @property enabled when false, outbound messages are counted and dropped, and nothing
 *   connects to the broker.
 * @property mqtt the broker consumers such as Home Assistant listen on. Separate from
 *   the ingest broker, though nothing stops both pointing at one.
 * @property retry how a broker outage is ridden out.
 */
@ConfigurationProperties("pipeline.publish")
data class PublishDatafill(
    val enabled: Boolean = true,
    val mqtt: Mqtt = Mqtt(),
    val retry: Retry = Retry(),
) {
    /**
     * @property clientId must differ from the ingest adapter's, since a broker drops the
     *   older of two connections sharing an id.
     * @property connectTimeout how long a connection attempt may take before the publish
     *   counts as failed.
     * @property completionTimeout how long a publish may wait for the broker to confirm it.
     */
    data class Mqtt(
        val url: String = "tcp://localhost:1883",
        val clientId: String = "iot-pipeline-publisher",
        val username: String? = null,
        val password: String? = null,
        val qos: Int = 1,
        val connectTimeout: Duration = Duration.ofSeconds(5),
        val completionTimeout: Duration = Duration.ofSeconds(10),
    ) {
        init {
            require(url.isNotBlank()) { "pipeline.publish.mqtt.url must not be blank" }
            require(clientId.isNotBlank()) { "pipeline.publish.mqtt.client-id must not be blank" }
            require(qos in 0..2) { "pipeline.publish.mqtt.qos must be 0, 1 or 2" }
            require(connectTimeout.seconds >= 1) { "pipeline.publish.mqtt.connect-timeout must be at least 1s" }
            require(!completionTimeout.isNegative && !completionTimeout.isZero) {
                "pipeline.publish.mqtt.completion-timeout must be positive"
            }
        }
    }

    /**
     * @property initialBackoff the wait before the first retry once a publish fails.
     * @property maxBackoff the longest wait between retries. The wait doubles after each
     *   failed retry up to this, so a long outage costs one attempt every [maxBackoff].
     */
    data class Retry(
        val initialBackoff: Duration = Duration.ofSeconds(1),
        val maxBackoff: Duration = Duration.ofMinutes(1),
    ) {
        init {
            require(!initialBackoff.isNegative && !initialBackoff.isZero) {
                "pipeline.publish.retry.initial-backoff must be positive"
            }
            require(maxBackoff >= initialBackoff) {
                "pipeline.publish.retry.max-backoff must not be shorter than initial-backoff"
            }
        }
    }
}
