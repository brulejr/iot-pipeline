/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.recommend

/**
 * What has reached the recommendation engine since startup.
 *
 * @property recorded readings counted as sightings.
 * @property unidentified readings dropped because they carried no device identity.
 */
data class RecommendationStats(val recorded: Long, val unidentified: Long)
