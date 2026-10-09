/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify.seed

import io.brulejr.iotpipeline.classify.CurationResult
import io.brulejr.iotpipeline.classify.ModelRecord
import io.brulejr.iotpipeline.classify.ModelRegistryPort
import io.brulejr.iotpipeline.classify.validateCuration
import io.brulejr.iotpipeline.fingerprint.sha256Hex
import org.slf4j.LoggerFactory
import org.springframework.core.io.ResourceLoader
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

/**
 * Restores curated sensor mappings from a file.
 *
 * Mappings are the only hand-made data in the pipeline: everything else is derived from
 * live traffic and rebuilds itself. Emptying the store would otherwise lose them with
 * nothing to restore from.
 *
 * Seeding never overwrites a model that already carries mappings, so curation through
 * the API stays authoritative and a stale seed file cannot silently revert it.
 */
class ModelSeeder(
    private val registry: ModelRegistryPort,
    private val datafill: ModelSeedDatafill,
    private val jsonMapper: JsonMapper,
    private val resourceLoader: ResourceLoader,
) {

    private val log = LoggerFactory.getLogger(ModelSeeder::class.java)

    fun seed(): SeedOutcome {
        if (!datafill.enabled) return SeedOutcome()
        val resource = resourceLoader.getResource(datafill.location)
        if (!resource.exists()) {
            log.debug("No model seed at {}; nothing to restore", datafill.location)
            return SeedOutcome()
        }

        val seeds: List<ModelSeed> = resource.inputStream.use {
            jsonMapper.readValue(it, Array<ModelSeed>::class.java).toList()
        }
        seeds.forEach(::verify)

        var restored = 0
        var curated = 0
        var leftAlone = 0
        seeds.forEach { seed ->
            val existing = registry.find(seed.fingerprint)
            when {
                existing == null -> {
                    registry.insertIfAbsent(seed.toRecord())
                    restored++
                }
                // Curation through the API wins; a seed only fills a gap.
                existing.recognised -> leftAlone++
                else -> {
                    when (val result = registry.curate(seed.fingerprint, seed.sensors)) {
                        is CurationResult.Curated -> curated++
                        else -> error("Seed for ${seed.describe()} was refused: $result")
                    }
                }
            }
        }

        val outcome = SeedOutcome(restored, curated, leftAlone)
        log.info(
            "Seeded models from {}: {} restored, {} curated, {} already curated",
            datafill.location, outcome.restored, outcome.curated, outcome.leftAlone,
        )
        return outcome
    }

    /**
     * Rejects a seed entry before anything is written.
     *
     * A hand-edited file is the likely source of both faults here, and both would be
     * silent: a fingerprint that is not the hash of its own structure describes a model
     * no live reading can ever match, and a mapping naming a missing field produces a
     * model that reports itself recognised while parsing to nothing.
     */
    private fun verify(seed: ModelSeed) {
        val expected = sha256Hex(seed.structure)
        require(seed.fingerprint == expected) {
            "Seed for ${seed.describe()} has fingerprint ${seed.fingerprint}, but its " +
                "structure hashes to $expected. No reading would ever match it."
        }
        validateCuration(seed.toRecord(), seed.sensors, jsonMapper)?.let { invalid ->
            error(
                "Seed for ${seed.describe()} is invalid: " +
                    invalid.problems.joinToString("; ") { "${it.field}: ${it.reason}" },
            )
        }
    }

    private fun ModelSeed.describe() = "${name ?: "unnamed"} [${fingerprint.take(12)}]"

    private fun ModelSeed.toRecord() = ModelRecord(
        fingerprint = fingerprint,
        source = source,
        name = name,
        structure = structure,
        // The original sighting is not in the file; this is when it was restored.
        discoveredAt = Instant.now(),
        sensors = sensors,
    )
}
