/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify

import io.brulejr.iotpipeline.pipeline.RegistryType
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @property registry where discovered models and their curation are kept.
 * @property seed how curated mappings are restored at startup.
 */
@ConfigurationProperties("pipeline.classification")
data class ClassificationDatafill(
    val registry: RegistryType = RegistryType.MONGO,
    val seed: Seed = Seed(),
) {
    /**
     * @property enabled when false the seed file is not read at all.
     * @property location a Spring resource location, so `classpath:` keeps the seed with
     *   the code while `file:` points at one managed outside it. A location that does not
     *   exist is not an error: most deployments have nothing to restore.
     */
    data class Seed(
        val enabled: Boolean = true,
        val location: String = "classpath:model-seed.json",
    ) {
        init {
            require(location.isNotBlank()) { "pipeline.classification.seed.location must not be blank" }
        }
    }
}
