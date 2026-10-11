/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote.seed

import io.brulejr.iotpipeline.promote.PromotedDevice
import io.brulejr.iotpipeline.promote.PromotionPort
import io.brulejr.iotpipeline.promote.PromotionResult
import org.slf4j.LoggerFactory
import org.springframework.core.io.ResourceLoader
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

/**
 * Restores device approvals from a file.
 *
 * Approvals are hand-made decisions, like the sensor mappings: nothing in the pipeline
 * can derive them, and emptying the store would silence every device until someone
 * approved them all again.
 *
 * Seeding never overwrites an approval already in the store, so promotion through the
 * API stays authoritative and a stale file cannot silently revert a corrected label.
 */
class PromotionSeeder(
    private val promotions: PromotionPort,
    private val datafill: PromotionSeedDatafill,
    private val jsonMapper: JsonMapper,
    private val resourceLoader: ResourceLoader,
) {

    private val log = LoggerFactory.getLogger(PromotionSeeder::class.java)

    fun seed(): PromotionSeedOutcome {
        if (!datafill.enabled) return PromotionSeedOutcome()
        val resource = resourceLoader.getResource(datafill.location)
        if (!resource.exists()) {
            log.debug("No promotion seed at {}; nothing to restore", datafill.location)
            return PromotionSeedOutcome()
        }

        val seeds: List<PromotionSeed> = resource.inputStream.use {
            jsonMapper.readValue(it, Array<PromotionSeed>::class.java).toList()
        }

        var restored = 0
        var leftAlone = 0
        seeds.forEach { seed ->
            if (promotions.find(seed.deviceKey) != null) {
                leftAlone++
                return@forEach
            }
            // Refused entries fail startup rather than leaving a device quietly
            // unapproved, which would look identical to a device nobody has reviewed.
            when (val result = promotions.promote(seed.toDevice())) {
                is PromotionResult.Promoted -> restored++
                is PromotionResult.Invalid -> error(
                    "Promotion seed for ${seed.deviceKey.id} is invalid: " +
                        result.problems.joinToString("; ") { "${it.field}: ${it.reason}" },
                )
            }
        }

        val outcome = PromotionSeedOutcome(restored, leftAlone)
        log.info(
            "Seeded promotions from {}: {} restored, {} already approved",
            datafill.location, outcome.restored, outcome.leftAlone,
        )
        return outcome
    }

    private fun PromotionSeed.toDevice() = PromotedDevice(
        deviceKey = deviceKey,
        name = name,
        type = type,
        area = area,
        // When it was restored, not when the approval was originally given.
        promotedAt = Instant.now(),
    )
}
