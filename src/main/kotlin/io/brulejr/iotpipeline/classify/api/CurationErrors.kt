/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify.api

import io.brulejr.iotpipeline.classify.CurationProblem

/** Returned with 400 when a submitted mapping is refused. */
data class CurationErrors(val message: String, val problems: List<CurationProblem>)
