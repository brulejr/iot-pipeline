/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify.seed

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @property enabled when false the seed file is not read at all.
 * @property location a Spring resource location, so `classpath:` keeps the seed with the
 *   code while `file:` points at one managed outside it. A location that does not exist
 *   is not an error: most deployments have nothing to restore.
 */
@ConfigurationProperties("pipeline.model-seed")
data class ModelSeedDatafill(
    val enabled: Boolean = true,
    val location: String = "classpath:model-seed.json",
)
