/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.dedupe

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * @property enabled when false every reading passes, which is useful when diagnosing
 *   whether dedupe is discarding something it should not.
 * @property window how long a reading stays remembered. A sensor transmitting every
 *   30s, repeated by two receivers within milliseconds of each other, needs only a
 *   window long enough to span the receivers, not the transmission interval.
 * @property maximumDevices cap on remembered transmitters, so a noisy band cannot grow
 *   the cache without bound.
 */
@ConfigurationProperties("pipeline.dedupe")
data class DedupeDatafill(
    val enabled: Boolean = true,
    val window: Duration = Duration.ofSeconds(5),
    val maximumDevices: Long = 10_000,
)
