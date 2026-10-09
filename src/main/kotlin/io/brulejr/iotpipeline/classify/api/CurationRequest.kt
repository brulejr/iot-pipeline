/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify.api

import io.brulejr.iotpipeline.classify.SensorMapping

/**
 * Body of a curation request: everything a human decides about a model.
 *
 * @property category what kind of thing the model is, e.g. `weather` or `security`.
 *   Required, because curation is the moment someone decides.
 * @property sensors how to read values out of the payload. An empty list leaves the
 *   model unrecognised, which is the way to undo a curation.
 */
data class CurationRequest(
    val category: String,
    val sensors: List<SensorMapping> = emptyList(),
)
