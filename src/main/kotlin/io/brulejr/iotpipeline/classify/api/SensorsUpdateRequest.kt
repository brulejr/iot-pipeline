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

/** Body of a curation request. */
data class SensorsUpdateRequest(val sensors: List<SensorMapping> = emptyList())
