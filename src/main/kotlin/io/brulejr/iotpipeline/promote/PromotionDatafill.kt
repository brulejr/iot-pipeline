/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote

import io.brulejr.iotpipeline.pipeline.RegistryType
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @property registry where device approvals are kept.
 * @property seed how approvals are restored at startup.
 */
@ConfigurationProperties("pipeline.promotion")
data class PromotionDatafill(
    val registry: RegistryType = RegistryType.POSTGRES,
    val seed: Seed = Seed(),
) {
    /**
     * @property enabled when false the seed file is not read at all.
     * @property location a Spring resource location. A location that does not exist is
     *   not an error: most deployments have nothing to restore.
     */
    data class Seed(
        val enabled: Boolean = true,
        val location: String = "classpath:promotion-seed.json",
    ) {
        init {
            require(location.isNotBlank()) { "pipeline.promotion.seed.location must not be blank" }
        }
    }
}
