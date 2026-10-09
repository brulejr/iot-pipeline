/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify.seed

import io.brulejr.iotpipeline.classify.SensorMapping

/**
 * One model as a seed file records it.
 *
 * Deliberately the same shape as a model in `GET /api/models`, so that response can be
 * saved and fed straight back in. Extra fields in the file, such as the computed
 * `recognised`, are ignored.
 *
 * @property structure the canonical payload structure. Carried because a seed has to be
 *   able to restore a model the pipeline has not seen since the store was emptied, and
 *   a model cannot be curated before it exists.
 * @property category what kind of thing the model is. Required and undefaulted, so a
 *   seed file missing it fails at startup rather than restoring models that claim to be
 *   curated while saying nothing about what they are.
 */
data class ModelSeed(
    val fingerprint: String,
    val source: String,
    val name: String? = null,
    val structure: String,
    val category: String,
    val sensors: List<SensorMapping> = emptyList(),
)
